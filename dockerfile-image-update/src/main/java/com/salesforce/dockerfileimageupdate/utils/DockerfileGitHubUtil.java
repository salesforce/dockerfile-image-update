/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.utils;

import com.google.common.collect.Multimap;
import com.salesforce.dockerfileimageupdate.model.*;
import com.salesforce.dockerfileimageupdate.repository.GitHub;
import com.salesforce.dockerfileimageupdate.search.GitHubImageSearchTermList;
import com.salesforce.dockerfileimageupdate.storage.GitHubJsonStore;
import net.sourceforge.argparse4j.inf.*;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.github.*;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

/**
 * Created by minho.park on 7/22/16.
 */
public class DockerfileGitHubUtil {
    private static final Logger log = LoggerFactory.getLogger(DockerfileGitHubUtil.class);
    private final GitHubUtil gitHubUtil;
    private static final String NO_DFIU = "no-dfiu";

    public DockerfileGitHubUtil(GitHubUtil gitHubUtil) {
        this.gitHubUtil = gitHubUtil;
    }

    protected GitHubUtil getGitHubUtil() { return gitHubUtil; }

    /**
     * Return an existing fork in the current user's org or create one if it does not exist
     *
     * THIS METHOD ENSURES THAT THE FORK IS DIRECTLY FROM THE PARENT!
     *
     * @param parent the proposed/current fork parent
     * @return existing fork or new fork
     */
    public GHRepository getOrCreateFork(GHRepository parent) {
        for (GHRepository fork : parent.listForks()) {
            try {
                if (thisUserIsOwner(fork)) {
                    // Check to see if latest sha from parent is in the tree of the fork. If not,
                    // it is stale. If stale, an administrator will need to rebuild the commit
                    // graph. If they don't do that, we get a 422 Reference update failed
                    // https://docs.github.com/enterprise/2.22/rest/reference/git#create-a-reference
                    // Updating the default branch of the fork to the sha results in:
                    // `422 Object does not exist`
                    // https://docs.github.com/enterprise/2.22/rest/reference/git#update-a-reference
                    // Confirmed that this does not occur for another similarly old fork
                    if (GitHub.isForkStale(parent, fork)) {
                        log.warn("Fork's commit graph is inconsistent," +
                                " you'll likely see a 422 error. Fork info: {}", fork);
                    } else {
                        log.info("Fork exists, so we'll reuse it. Fork info: {}", fork);
                    }
                    // NOTE: listForks() appears to miss information like parent data and GHContent parent doesn't have isArchived()
                    return fork;
                }
            } catch (IOException ioException) {
                log.error("Could not determine user to see if we can fork. Skipping.", ioException);
                return null;
            }
        }
        log.info("Forking repo: {}", parent);
        return gitHubUtil.createFork(parent);
    }

    public GHMyself getMyself() throws IOException {
        return gitHubUtil.getMyself();
    }

    public GHRepository getRepo(String repoName) throws IOException {
        return gitHubUtil.getRepo(repoName);
    }

    public Optional<List<PagedSearchIterable<GHContent>>> findFilesWithImage(
            String image,
            Map<String, Boolean> orgsToIncludeOrExclude,
            Integer gitApiSearchLimit) throws IOException {
        GHContentSearchBuilder search = gitHubUtil.startSearch();
        // Filename search appears to yield better / more results than language:Dockerfile
        // Root cause: linguist doesn't currently deal with prefixes of files:
        // https://github.com/github/linguist/issues/4566
        search.filename("Dockerfile");
        if (!orgsToIncludeOrExclude.isEmpty()) {
            StringBuilder includeOrExcludeOrgsQuery = new StringBuilder();
            for (Map.Entry<String, Boolean> org : orgsToIncludeOrExclude.entrySet()){
                if (org.getKey() != null) {
                    if (org.getValue()) {
                        log.info("Including the org {} in the search.", org.getKey());
                        includeOrExcludeOrgsQuery.append(String.format("user:%s ", org.getKey()));
                    } else {
                        log.info("Excluding the org {} from the search.", org.getKey());
                        includeOrExcludeOrgsQuery.append(String.format("-org:%s ", org.getKey()));
                    }
                }
            }
            search.q(includeOrExcludeOrgsQuery.toString());
        }
        if (image.substring(image.lastIndexOf(' ') + 1).length() <= 1) {
            throw new IOException("Invalid image name.");
        }
        List<String> terms = GitHubImageSearchTermList.getSearchTerms(image);
        log.info("Searching for {} with terms: {}", image, terms);
        terms.forEach(search::q);
        PagedSearchIterable<GHContent> files = search.list();
        int totalCount = files.getTotalCount();
        log.info("Number of files found for {}: {}", image, totalCount);
        if (totalCount > gitApiSearchLimit
            && orgsToIncludeOrExclude.size() == 1
            && orgsToIncludeOrExclude
               .entrySet()
               .stream()
               .findFirst()
               .get()
               .getKey() != null
            && orgsToIncludeOrExclude
               .entrySet()
               .stream()
               .findFirst()
               .get()
               .getValue()
        ) {
            String orgName = orgsToIncludeOrExclude
                    .entrySet()
                    .stream()
                    .findFirst()
                    .get()
                    .getKey();
            log.warn("Number of search results for a single org {} is above {}! The GitHub Search API will only return around 1000 results - https://developer.github.com/v3/search/#about-the-search-api",
                    orgName, gitApiSearchLimit);
        } else if (totalCount > gitApiSearchLimit) {
            log.info("The number of files returned is greater than the git API search limit"
                    + " of {}. The orgs with the maximum number of hits will be recursively removed"
                    + " to reduce the search space. For every org that is excluded, a separate "
                    + "search will be performed specific to that org.", gitApiSearchLimit);
            return getSearchResultsExcludingOrgWithMostHits(image, files, orgsToIncludeOrExclude, gitApiSearchLimit);
        }
        List<PagedSearchIterable<GHContent>> filesList = new ArrayList<>();
        filesList.add(files);
        return Optional.of(filesList);
    }

    protected String getOrgNameWithMaximumHits(PagedSearchIterable<GHContent> files) throws IOException {

        return files.toList().stream()
                .collect(Collectors.groupingBy(ghContent -> ghContent.getOwner().getOwnerName(), Collectors.counting()))
                .entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .get()
                .getKey();
    }

    protected Optional<List<PagedSearchIterable<GHContent>>> getSearchResultsExcludingOrgWithMostHits(
            String image,
            PagedSearchIterable<GHContent> files,
            Map<String, Boolean> orgsToExclude,
            Integer gitApiSearchLimit) throws IOException {
        List<PagedSearchIterable<GHContent>> allContentsWithImage = new ArrayList<>();
        String orgWithMaximumHits = getOrgNameWithMaximumHits(files);
        log.info("The org with the maximum number of hits is {}", orgWithMaximumHits);

        Map<String, Boolean> orgsToInclude = new HashMap<>();
        orgsToInclude.put(orgWithMaximumHits, true);
        log.info("Running search only for the org with maximum hits.");
        Optional<List<PagedSearchIterable<GHContent>>> contentsForOrgWithMaximumHits;
        contentsForOrgWithMaximumHits = findFilesWithImage(image, orgsToInclude, gitApiSearchLimit);

        final Map<String, Boolean> orgsToExcludeFromSearch = new HashMap<>(orgsToExclude);
        orgsToExcludeFromSearch.put(orgWithMaximumHits, false);
        log.info("Running search by excluding the orgs {}.", orgsToExcludeFromSearch.keySet());
        Optional<List<PagedSearchIterable<GHContent>>> contentsExcludingOrgWithMaximumHits;
        contentsExcludingOrgWithMaximumHits = findFilesWithImage(image, orgsToExcludeFromSearch, gitApiSearchLimit);
        if (contentsForOrgWithMaximumHits.isPresent()) {
            allContentsWithImage.addAll(contentsForOrgWithMaximumHits.get());
        }
        if (contentsExcludingOrgWithMaximumHits.isPresent()) {
            allContentsWithImage.addAll(contentsExcludingOrgWithMaximumHits.get());
        }
        return Optional.of(allContentsWithImage);
    }

    /* Workaround: The GitHub API caches API calls for up to 60 seconds, so back-to-back API calls with the same
     * command will return the same thing. i.e. the above command listRepositories will return the same output if
     * this tool is invoked twice in a row, even though it should return different lists, because of the new forks.
     *
     * The GitHub API itself actually provides a workaround: check
     * https://developer.github.com/guides/getting-started/#conditional-requests
     * However, the GitHub API library uses an outdated version of Okhttp, and Okhttp no longer supports
     * OkUrlFactory, which is required to specify the cache. In other words, we cannot flush the cache.
     *
     * Instead, we wait for 60 seconds if the list retrieved is not the list we want.
     */
    public List<GHRepository> getGHRepositories(Multimap<String, String> pathToDockerfileInParentRepo,
                                                            GHMyself currentUser) throws InterruptedException {
        return gitHubUtil.getGHRepositories(pathToDockerfileInParentRepo, currentUser);
    }

    public void modifyAllOnGithub(GHRepository repo, String branch,
                                  String img, String tag, String ignoreImageString)
            throws IOException, InterruptedException {
        List<GHContent> tree = null;

        /* There are issues with the GitHub API returning that the GitHub repository exists, but has no content,
         * when we try to pull on it the moment it is created. The system must wait a short time before we can move on.
         */
        for (int i = 0; i < 5; i++) {
            try {
                tree = repo.getDirectoryContent(".", branch);
                if (tree != null) {
                    break;
                }
            } catch (FileNotFoundException e1) {
                log.warn("Content in repository not created yet. Retrying connection to fork...");
                getGitHubUtil().waitFor(TimeUnit.SECONDS.toMillis(1));
            }
        }
        if (tree != null) {
            for (GHContent con : tree) {
                modifyOnGithubRecursive(repo, con, branch, img, tag, ignoreImageString);
            }
        }
    }

    protected void modifyOnGithubRecursive(GHRepository repo, GHContent content,
                                           String branch, String img, String tag,
                                           String ignoreImageString) throws IOException {
        /* If we have a submodule; we want to skip.
           Content is submodule when the type is file, but content.getDownloadUrl() is null.
         */
        if (content.isFile() && content.getDownloadUrl() != null) {
            modifyOnGithub(content, branch, img, tag, "", ignoreImageString);
        } else if(content.isDirectory()) {
            for (GHContent newContent : repo.getDirectoryContent(content.getPath(), branch)) {
                modifyOnGithubRecursive(repo, newContent, branch, img, tag, ignoreImageString);
            }
        } else {
            // The only other case is if we have a file, but content.getDownloadUrl() is null
            log.info("Skipping submodule {}", content.getName());
        }
    }

    public GHContent tryRetrievingContent(GHRepository repo, String path, String branch) throws InterruptedException {
        return gitHubUtil.tryRetrievingContent(repo, path, branch);
    }

    public GHBlob tryRetrievingBlob(GHRepository repo, String path, String branch)
            throws IOException {
        return gitHubUtil.tryRetrievingBlob(repo, path, branch);
    }

    public void modifyOnGithub(GHContent content,
                               String branch, String img, String tag,
                               String customMessage, String ignoreImageString) throws IOException {
        try (InputStream stream = content.read();
             InputStreamReader streamR = new InputStreamReader(stream);
             BufferedReader reader = new BufferedReader(streamR)) {
            findImagesAndFix(content, branch, img, tag, customMessage, reader, ignoreImageString);
        }
    }

    protected void findImagesAndFix(GHContent content, String branch, String img,
                                    String tag, String customMessage, BufferedReader reader,
                                    String ignoreImageString) throws IOException {
        StringBuilder strB = new StringBuilder();
        boolean modified = rewriteDockerfile(img, tag, reader, strB, ignoreImageString);
        if (modified) {
            content.update(strB.toString(),
                    "Fix Dockerfile base image in /" + content.getPath() + "\n\n" + customMessage, branch);
        }
    }

    protected boolean rewriteDockerfile(String img, String tag,
                                        BufferedReader reader, StringBuilder strB,
                                        String ignoreImageString) throws IOException {
        String line;
        boolean modified = false;
        while ( (line = reader.readLine()) != null ) {
            if (ignorePRCommentPresent(line, ignoreImageString)) {
                strB.append(line).append("\n");

                // Skip the next Line so it is not processed for PR creation
                line = reader.readLine();
                strB.append(line).append("\n");
                continue;
            }
            /* Once true, should stay true. */
            modified = changeIfDockerfileBaseImageLine(img, tag, strB, line) || modified;
        }
        return modified;
    }

    /**
     * This method will read a line and see if the line contains a FROM instruction with the specified
     * {@code imageToFind}. If the image does not have the given {@code tag} then {@code stringBuilder}
     * will get a modified version of the line with the new {@code tag}. We return {@code true} in this
     * instance.
     *
     * If the inbound {@code line} does not qualify for changes or if the tag is already correct, the
     * {@code stringBuilder} will get {@code line} added to it. We return {@code false} in this instance.
     *
     *
     * @param imageToFind the Docker image that may require a tag update
     * @param tag the Docker tag that we'd like the image to have
     * @param stringBuilder the stringBuilder to accumulate the output lines for the pull request
     * @param line the inbound line from the Dockerfile
     * @return Whether we've modified the {@code line} that goes into {@code stringBuilder}
     */
    protected boolean changeIfDockerfileBaseImageLine(String imageToFind, String tag,
                                                      StringBuilder stringBuilder, String line) {
        boolean modified = false;
        String outputLine = line;

        // Only check/modify lines which contain a FROM instruction
        if (FromInstruction.isFromInstruction(line)) {
            FromInstruction fromInstruction = new FromInstruction(line);

            if (fromInstruction.hasBaseImage(imageToFind) &&
                    fromInstruction.hasADifferentTag(tag)) {
                fromInstruction = fromInstruction.getFromInstructionWithNewTag(tag);
                modified = true;
            }
            outputLine = fromInstruction.toString();
        }
        stringBuilder.append(outputLine).append("\n");
        return modified;
    }

    /**
     * Determines whether a comment before FROM line has {@code ignoreImageString} to ignore creating dfiu PR
     * If {@code ignoreImageString} present in comment, PR should be ignored
     * If {@code ignoreImageString} is empty, then by default 'no-dfiu' comment will be searched
     * @param line line to search for comment
     * @param ignoreImageString comment to search
     * @return {@code true} if comment is found
     */
    protected boolean ignorePRCommentPresent(String line, String ignoreImageString) {
        final String tester = Optional.ofNullable(ignoreImageString).filter(StringUtils::isNotBlank).orElse(NO_DFIU);
        return StringUtils.isNotBlank(line) && line.trim().startsWith("#") && line.substring(line.indexOf("#") + 1).trim().equals(tester);
    }

    public GitHubJsonStore getGitHubJsonStore(String store) {
        return new GitHubJsonStore(this.gitHubUtil, store);
    }

    public void createPullReq(GHRepository origRepo,
                              String branch, GHRepository forkRepo,
                              PullRequestInfo pullRequestInfo) throws InterruptedException, IOException {
        // TODO: This may loop forever in the event of constant -1 pullRequestExitCodes...
        while (true) {
            int pullRequestExitCode = gitHubUtil.createPullReq(origRepo,
                    branch, forkRepo, pullRequestInfo.getTitle(), pullRequestInfo.getBody());
            if (pullRequestExitCode == 0) {
                return;
            } else if (pullRequestExitCode == 1) {
                gitHubUtil.safeDeleteRepo(forkRepo);
                return;
            }
        }
    }

    /**
     * Get the pull request from this repository that corresponds to this branch. A prerequisite should be that the repository
     * passed in here should have already came from the expected parent. We will not check this again here.
     *
     * @param repository the repository which should come from an expected parent
     * @param gitForkBranch the branch name which was derived from the image to update
     * @return Optional GHPullRequest if we've found one
     */
    public Optional<GHPullRequest> getPullRequestForImageBranch(GHRepository repository, GitForkBranch gitForkBranch) {
        PagedIterable<GHPullRequest> pullRequests =
                repository.queryPullRequests().state(GHIssueState.OPEN).head(gitForkBranch.getBranchName()).list();
        for (GHPullRequest pullRequest : pullRequests) {
            // There can be only one since it is based on branch.
            return Optional.of(pullRequest);
        }
        return Optional.empty();
    }

    /**
     * Create or update the desired {@code gitForkBranch} in {@code fork} based off of the {@code parent} repo's
     * default branch to ensure that {@code gitForkBranch} is on the latest commit.
     *
     * You must have branches in a repo in order to create a ref per:
     * https://developer.github.com/enterprise/2.19/v3/git/refs/#create-a-reference
     *
     * Generally, we can assume that a fork should have branches so if it does not have branches, we're still
     * waiting for GitHub to finish replicating the tree behind the scenes.
     *
     * @param parent parent repo to base from
     * @param fork fork repo where we'll create or modify the {@code gitForkBranch}
     * @param gitForkBranch desired branch to create or update based on the parent's default branch
     * @throws IOException if there is any failure while I/O.
     * @throws InterruptedException if interrupted during updating forked branch
     */
    public void createOrUpdateForkBranchToParentDefault(GHRepository parent, GHRepository fork, GitForkBranch gitForkBranch) throws IOException, InterruptedException {
        GHBranch parentBranch = parent.getBranch(parent.getDefaultBranch());
        String sha1 = parentBranch.getSHA1();
        gitHubUtil.tryRetrievingBranch(fork, parent.getDefaultBranch());
        String branchRefName = String.format("refs/heads/%s", gitForkBranch.getBranchName());
        if (gitHubUtil.repoHasBranch(fork, gitForkBranch.getBranchName())) {
            fork.getRef(branchRefName).updateTo(sha1, true);
        } else {
            fork.createRef(branchRefName, sha1);
        }
    }

    /**
     * Determines whether the user we're logged in as is the repo owner
     *
     * @param repo the repo to check
     * @return are we the repo owner?
     * @throws IOException when attempting to check our user details
     */
    public boolean thisUserIsOwner(GHRepository repo) throws IOException {
        String repoOwner = repo.getOwnerName();
        GHMyself myself = gitHubUtil.getMyself();
        if (myself == null) {
            throw new IOException("Could not retrieve authenticated user.");
        }
        String myselfLogin = myself.getLogin();
        return repoOwner.equals(myselfLogin);
    }

    /**
     * Get GHContents for a provided image in the provided GitHub org.
     *
     * @param org GitHub organization
     * @param img image to find
     * @param gitApiSearchLimit git api search limit
     * @throws IOException if there is any failure while I/O from git.
     * @throws InterruptedException if interrupted while fetching git content
     * @return {@code Optional} of {@code PagedSearchIterable}
     */
    public Optional<List<PagedSearchIterable<GHContent>>> getGHContents(String org, String img, Integer gitApiSearchLimit)
            throws IOException, InterruptedException {
        Optional<List<PagedSearchIterable<GHContent>>> contentsWithImage = Optional.empty();
        Map<String, Boolean> orgsToIncludeInSearch = new HashMap<>();
        if (org != null) {
            // If there is a Git org specified, that needs to be included in the search query. In
            // the orgsToIncludeInSearch a true value associated with an org name ensures that
            // the org gets included in the search query.
            orgsToIncludeInSearch.put(org, true);
        }
        for (int i = 0; i < 5; i++) {
            contentsWithImage = findFilesWithImage(img, orgsToIncludeInSearch, gitApiSearchLimit);
            if (contentsWithImage
                    .orElseThrow(IOException::new)
                    .stream()
                    .findAny()
                    .get()
                    .getTotalCount() > 0) {
                break;
            } else {
                getGitHubUtil().waitFor(TimeUnit.SECONDS.toMillis(1));
            }
        }
        int numOfContentsFound = contentsWithImage
                .orElseThrow(IOException::new)
                .stream()
                .mapToInt(PagedSearchIterable::getTotalCount)
                .sum();
        if (numOfContentsFound <= 0) {
            log.info("Could not find any repositories with given image: {}", img);
            return Optional.empty();
        }
        return contentsWithImage;
    }

    public void changeDockerfiles(Namespace ns,
                                     Multimap<String, GitHubContentToProcess> pathToDockerfilesInParentRepo,
                                     GitHubContentToProcess gitHubContentToProcess,
                                     List<String> skippedRepos,
                                     GitForkBranch gitForkBranch) throws IOException,
            InterruptedException {
        // Should we skip doing a getRepository just to fill in the parent value? We already know this to be the parent...
        GHRepository parent = gitHubContentToProcess.getParent();
        GHRepository forkedRepo = gitHubContentToProcess.getFork();
        String parentName = parent.getFullName();

        log.info("Fixing Dockerfiles in {} to PR to {}", forkedRepo.getFullName(), parent.getFullName());

        createOrUpdateForkBranchToParentDefault(parent, forkedRepo, gitForkBranch);

        // loop through all the Dockerfiles in the same repo
        boolean isContentModified = false;
        boolean isRepoSkipped = true;
        for (GitHubContentToProcess forkWithCurrentContentPath : pathToDockerfilesInParentRepo.get(parentName)) {
            String pathToDockerfile = forkWithCurrentContentPath.getContentPath();
            GHContent content = tryRetrievingContent(forkedRepo, pathToDockerfile, gitForkBranch.getBranchName());
            if (content == null) {
                log.info("No Dockerfile found at path: '{}'", pathToDockerfile);
            } else {
                modifyOnGithub(content, gitForkBranch.getBranchName(), gitForkBranch.getImageName(), gitForkBranch.getImageTag(),
                        ns.get(Constants.GIT_ADDITIONAL_COMMIT_MESSAGE), ns.get(Constants.IGNORE_IMAGE_STRING));
                isContentModified = true;
                isRepoSkipped = false;
            }
        }

        if (isRepoSkipped) {
            log.info("Skipping repo '{}' because contents of it's fork could not be retrieved. Moving ahead...",
                    parentName);
            skippedRepos.add(forkedRepo.getFullName());
        }

        if (isContentModified) {
            PullRequestInfo pullRequestInfo =
                    new PullRequestInfo(ns.get(Constants.GIT_PR_TITLE),
                            gitForkBranch.getImageName(),
                            gitForkBranch.getImageTag(),
                            ns.get(Constants.GIT_PR_BODY));

            createPullReq(parent,
                    gitForkBranch.getBranchName(),
                    forkedRepo,
                    pullRequestInfo);
        }
    }
}
