/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.utils;

import com.google.common.collect.Multimap;
import com.salesforce.dockerfileimageupdate.model.FromInstruction;
import com.salesforce.dockerfileimageupdate.model.GitForkBranch;
import com.salesforce.dockerfileimageupdate.model.PullRequestInfo;
import com.salesforce.dockerfileimageupdate.storage.GitHubJsonStore;
import org.kohsuke.github.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Created by minho.park on 7/22/16.
 */
public class DockerfileGitHubUtil {
    private static final Logger log = LoggerFactory.getLogger(DockerfileGitHubUtil.class);
    private final GitHubUtil gitHubUtil;

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
                    log.info("Fork exists, retrieving full info: {}", fork);
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

    public PagedSearchIterable<GHContent> findFilesWithImage(String query, String org) throws IOException {
        GHContentSearchBuilder search = gitHubUtil.startSearch();
        // Filename search appears to yield better / more results than language:Dockerfile
        // Root cause: linguist doesn't currently deal with prefixes of files:
        // https://github.com/github/linguist/issues/4566
        search.filename("Dockerfile");
        if (org != null) {
            search.user(org);
        }
        if (query.substring(query.lastIndexOf(' ') + 1).length() <= 1) {
            throw new IOException("Invalid image name.");
        }
        // Github search returns no results if your verbatim string contains more than two slashes
        int start = 0;
        while (start < query.length()) {
            int slash1 = query.indexOf('/', start);
            if (slash1 == -1) {
                search.q((start == 0 ? "\"FROM " : "\"") + query.substring(start) + "\"");
                break;
            }
            int slash2 = query.indexOf('/', slash1 + 1);
            if (slash2 == -1) {
                search.q((start == 0 ? "\"FROM " : "\"") + query.substring(start) + "\"");
                break;
            }
            search.q((start == 0 ? "\"FROM " : "\"") + query.substring(start, slash2) + "\"");
            start = slash2 + 1;
        }
        log.debug("Searching for {}", query);
        PagedSearchIterable<GHContent> files = search.list();
        int totalCount = files.getTotalCount();
        if (totalCount > 1000) {
            log.warn("Number of search results is above 1000! The GitHub Search API will only return around 1000 results - https://developer.github.com/v3/search/#about-the-search-api");
        }
        log.info("Number of files found for {}:{}", query, totalCount);
        return files;
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
                                  String img, String tag) throws IOException, InterruptedException {
        List<GHContent> tree = null;

        /* There are issues with the GitHub API returning that the GitHub repository exists, but has no content,
         * when we try to pull on it the moment it is created. The system must wait a short time before we can move on.
         */
        for (int i = 0; i < 5; i++) {
            try {
                tree = repo.getDirectoryContent(".", branch);
                break;
            } catch (FileNotFoundException e1) {
                log.warn("Content in repository not created yet. Retrying connection to fork...");
                getGitHubUtil().waitFor(TimeUnit.SECONDS.toMillis(1));
            }
        }
        for (GHContent con : tree) {
            modifyOnGithubRecursive(repo, con, branch, img, tag);
        }
    }

    protected void modifyOnGithubRecursive(GHRepository repo, GHContent content,
                                           String branch, String img, String tag) throws IOException {
        /* If we have a submodule; we want to skip.
           Content is submodule when the type is file, but content.getDownloadUrl() is null.
         */
        if (content.isFile() && content.getDownloadUrl() != null) {
            modifyOnGithub(content, branch, img, tag, "");
        } else if(content.isDirectory()) {
            for (GHContent newContent : repo.getDirectoryContent(content.getPath(), branch)) {
                modifyOnGithubRecursive(repo, newContent, branch, img, tag);
            }
        } else {
            // The only other case is if we have a file, but content.getDownloadUrl() is null
            log.info("Skipping submodule {}", content.getName());
        }
    }

    public GHContent tryRetrievingContent(GHRepository repo, String path, String branch) throws InterruptedException {
        return gitHubUtil.tryRetrievingContent(repo, path, branch);
    }

    public void modifyOnGithub(GHContent content,
                               String branch, String img, String tag, String customMessage) throws IOException {
        try (InputStream stream = content.read();
             InputStreamReader streamR = new InputStreamReader(stream);
             BufferedReader reader = new BufferedReader(streamR)) {
            findImagesAndFix(content, branch, img, tag, customMessage, reader);
        }
    }

    protected void findImagesAndFix(GHContent content,
                                    String branch, String img, String tag, String customMessage,
                                    BufferedReader reader) throws IOException {
        StringBuilder strB = new StringBuilder();
        boolean modified = rewriteDockerfile(img, tag, reader, strB);
        if (modified) {
            content.update(strB.toString(),
                    "Fix Dockerfile base image in /" + content.getPath() + "\n\n" + customMessage, branch);
        }
    }

    protected boolean rewriteDockerfile(String img, String tag, BufferedReader reader, StringBuilder strB) throws IOException {
        String line;
        boolean modified = false;
        while ( (line = reader.readLine()) != null ) {
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
     * @param imageToFind the Docker image that may require a tag update
     * @param tag the Docker tag that we'd like the image to have
     * @param stringBuilder the stringBuilder to accumulate the output lines for the pull request
     * @param line the inbound line from the Dockerfile
     * @return Whether we've modified the {@code line} that goes into {@code stringBuilder}
     */
    protected boolean changeIfDockerfileBaseImageLine(String imageToFind, String tag, StringBuilder stringBuilder, String line) {
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
     */
    public Optional<PagedSearchIterable<GHContent>> getGHContents(String org, String img)
            throws IOException, InterruptedException {
        PagedSearchIterable<GHContent> contentsWithImage = null;
        for (int i = 0; i < 5; i++) {
            contentsWithImage = findFilesWithImage(img, org);
            if (contentsWithImage.getTotalCount() > 0) {
                break;
            } else {
                getGitHubUtil().waitFor(TimeUnit.SECONDS.toMillis(1));
            }
        }

        int numOfContentsFound = contentsWithImage.getTotalCount();
        if (numOfContentsFound <= 0) {
            log.info("Could not find any repositories with given image: {}", img);
            return Optional.empty();
        }
        return Optional.of(contentsWithImage);
    }
}
