/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.subcommands.impl;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.salesforce.dockerfileimageupdate.SubCommand;
import com.salesforce.dockerfileimageupdate.repository.GitHub;
import com.salesforce.dockerfileimageupdate.subcommands.ExecutableWithNamespace;
import com.salesforce.dockerfileimageupdate.utils.Constants;
import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;
import com.salesforce.dockerfileimageupdate.utils.ResultsProcessor;
import net.sourceforge.argparse4j.inf.Namespace;
import org.kohsuke.github.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.*;

@SubCommand(help="updates all repositories' Dockerfiles",
        requiredParams = {Constants.STORE})
public class All implements ExecutableWithNamespace {
    private static final Logger log = LoggerFactory.getLogger(All.class);

    private DockerfileGitHubUtil dockerfileGitHubUtil;

    @Override
    public void execute(final Namespace ns, final DockerfileGitHubUtil dockerfileGitHubUtil) throws IOException, InterruptedException {
        loadDockerfileGithubUtil(dockerfileGitHubUtil);

        Map<String, String> imageToTagMap = new HashMap<>();
        Multimap<String, String> imagesFoundInParentRepo = ArrayListMultimap.create();
        Multimap<String, String> pathToDockerfilesInParentRepo = ArrayListMultimap.create();

        Set<Map.Entry<String, JsonElement>> imageToTagStore = parseStoreToImagesMap(ns.get(Constants.STORE));
        for (Map.Entry<String, JsonElement> imageToTag : imageToTagStore) {
            String image = imageToTag.getKey();
            log.info("Repositories with image {} being forked.", image);
            imageToTagMap.put(image, imageToTag.getValue().getAsString());
            PagedSearchIterable<GHContent> contentsWithImage =
                    this.dockerfileGitHubUtil.findFilesWithImage(image, ns.get(Constants.GIT_ORG));
            forkRepositoriesFound(pathToDockerfilesInParentRepo,
                    imagesFoundInParentRepo, contentsWithImage, image);
        }

        GHMyself currentUser = this.dockerfileGitHubUtil.getMyself();
        if (currentUser == null) {
            throw new IOException("Could not retrieve authenticated user.");
        }

        log.info("Retrieving all the forks...");
        List<GHRepository> listOfCurrUserRepos =
                dockerfileGitHubUtil.getGHRepositories(pathToDockerfilesInParentRepo, currentUser);

        List<IOException> exceptions = new ArrayList<>();
        List<String> skippedRepos = new ArrayList<>();

        for (GHRepository currUserRepo : listOfCurrUserRepos) {
            try {
                changeDockerfiles(ns, pathToDockerfilesInParentRepo, imagesFoundInParentRepo, imageToTagMap, currUserRepo,
                        skippedRepos);
            } catch (IOException e) {
                log.error(String.format("Error changing Dockerfile for %s", currUserRepo.getName()), e);
                exceptions.add(e);
            }
        }

        ResultsProcessor.processResults(skippedRepos, exceptions, log);
    }

    protected void loadDockerfileGithubUtil(DockerfileGitHubUtil _dockerfileGitHubUtil) {
        dockerfileGitHubUtil = _dockerfileGitHubUtil;
    }

    protected void forkRepositoriesFound(Multimap<String, String> pathToDockerfilesInParentRepo,
                                         Multimap<String, String> imagesFoundInParentRepo,
                                         PagedSearchIterable<GHContent> contentsWithImage,
                                         String image) throws IOException {
        log.info("Forking {} repositories...", contentsWithImage.getTotalCount());
        List<String> parentReposForked = new ArrayList<>();
        GHRepository parent;
        String parentRepoName = null;
        for (GHContent c : contentsWithImage) {
            /* Kohsuke's GitHub API library, when retrieving the forked repository, looks at the name of the parent to
             * retrieve. The issue with that is: GitHub, when forking two or more repositories with the same name,
             * automatically fixes the names to be unique (by appending "-#" to the end). Because of this edge case, we
             * cannot save the forks and iterate over the repositories; else, we end up missing/not updating the
             * repositories that were automatically fixed by GitHub. Instead, we save the names of the parent repos
             * in the map above, find the list of repositories under the authorized user, and iterate through that list.
             */
            parent = c.getOwner();
            parentRepoName = parent.getFullName();
            if (parent.isFork()) {
                log.warn("Skipping {} because it's a fork already. Sending a PR to a fork is unsupported at the moment.",
                        parentRepoName);
            } else {
                // fork the parent if not already forked
                if (!parentReposForked.contains(parentRepoName)) {
                    // TODO: Need to close PR!
                    GHRepository fork = dockerfileGitHubUtil.getOrCreateFork(parent);
                    GHPullRequest pr = getPullRequestWithPullReqIdentifier(parent);
                    // Only reason we close the existing PR, delete fork and re-fork, is because there is no way to
                    // determine if the existing fork is compatible with it's parent.
                    if (pr != null) {
                        // close the pull-request since the fork is out of date
                        log.info("closing existing pr: {}", pr.getUrl());
                        try {
                            pr.close();
                        } catch (IOException e) {
                            log.info("Issues closing the pull request '{}'. Moving ahead...", pr.getUrl());
                        }
                    }

                    if (fork == null) {
                        log.info("Could not fork {}", parentRepoName);
                    } else {
                        // Add repos to pathToDockerfilesInParentRepo and imagesFoundInParentRepo only if we forked it successfully.
                        pathToDockerfilesInParentRepo.put(parentRepoName, c.getPath());
                        imagesFoundInParentRepo.put(parentRepoName, image);
                        parentReposForked.add(parentRepoName);
                    }
                }
            }
        }

        log.info("Path to Dockerfiles in repo '{}': {}", parentRepoName, pathToDockerfilesInParentRepo);
        log.info("All images found in repo '{}': {}", parentRepoName, imagesFoundInParentRepo);
    }

    protected Set<Map.Entry<String, JsonElement>> parseStoreToImagesMap(String storeName)
            throws IOException, InterruptedException {
        GHMyself myself = dockerfileGitHubUtil.getMyself();
        String login = myself.getLogin();
        GHRepository store = dockerfileGitHubUtil.getRepo(Paths.get(login, storeName).toString());

        GHContent storeContent = dockerfileGitHubUtil.tryRetrievingContent(store, Constants.STORE_JSON_FILE,
                store.getDefaultBranch());

        if (storeContent == null) {
            return Collections.emptySet();
        }

        JsonElement json;
        try (InputStream stream = storeContent.read(); InputStreamReader streamR = new InputStreamReader(stream)) {
            try {
                json = JsonParser.parseReader(streamR);
            } catch (JsonParseException e) {
                log.warn("Not a JSON format store.");
                return Collections.emptySet();
            }
        }

        JsonElement imagesJson = json.getAsJsonObject().get("images");
        return imagesJson.getAsJsonObject().entrySet();
    }

    protected void changeDockerfiles(Namespace ns,
                                     Multimap<String, String> pathToDockerfilesInParentRepo,
                                     Multimap<String, String> imagesFoundInParentRepo,
                                     Map<String, String> imageToTagMap,
                                     GHRepository currUserRepo,
                                     List<String> skippedRepos) throws IOException, InterruptedException {
        /* The Github API does not provide the parent if retrieved through a list. If we want to access its parent,
         * we need to retrieve it once again.
         */
        GHRepository forkedRepo;
        if (currUserRepo.isFork()) {
            try {
                forkedRepo = dockerfileGitHubUtil.getRepo(currUserRepo.getFullName());
            } catch (FileNotFoundException e) {
                /* The edge case here: If a different command calls getGHRepositories, and then this command calls
                 * it again within 60 seconds, it will still have the same list of repositories (because of caching).
                 * However, between the previous and current call, if some of those repositories are deleted, the call
                 * above may cause a FileNotFoundException. This clause prevents that exception from stopping our call;
                 * we do not need to stop because getGHRepositories checks that we have all the repositories we need.
                 *
                 * The integration test calls the testParent -> testAllCommand -> testIdempotency, and the
                 * testIdempotency was failing because of this edge condition.
                 */

                log.warn("This repository does not exist. The list of repositories must be outdated, but the list" +
                        "contains the repositories we need, so we ignore this error.");
                return;
            }
        } else {
            return;
        }
        GHRepository parent = forkedRepo.getParent();

        if (GitHub.shouldNotProcessDockerfilesInRepo(pathToDockerfilesInParentRepo, parent)) return;

        log.info("Fixing Dockerfiles in {}...", forkedRepo.getFullName());
        String parentName = parent.getFullName();
        String branch = (ns.get(Constants.GIT_BRANCH) == null) ? forkedRepo.getDefaultBranch() : ns.get(Constants.GIT_BRANCH);

        String pathToDockerfile;
        String image;
        String tag;
        GHContent content;
        boolean isContentModified = false;
        boolean isRepoSkipped = true;

        Iterator<String> pathToDockerfileInParentRepoIterator = pathToDockerfilesInParentRepo.get(parentName).iterator();
        Iterator<String> imagesFoundInParentRepoIterator = imagesFoundInParentRepo.get(parentName).iterator();

        while (pathToDockerfileInParentRepoIterator.hasNext()) {
            pathToDockerfile = pathToDockerfileInParentRepoIterator.next();
            image = imagesFoundInParentRepoIterator.next();
            tag = imageToTagMap.get(image);
            log.info("pathToDockerfile: {} , image: {}, tag: {}", pathToDockerfile, image, tag);
            content = dockerfileGitHubUtil.tryRetrievingContent(forkedRepo, pathToDockerfile, branch);
            if (content == null) {
                log.info("No Dockerfile found at path: '{}'", pathToDockerfile);
            } else {
                dockerfileGitHubUtil.modifyOnGithub(content, branch, image, tag,
                        ns.get(Constants.GIT_ADDITIONAL_COMMIT_MESSAGE));
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
            dockerfileGitHubUtil.createPullReq(parent, branch, forkedRepo, ns.get(Constants.GIT_PR_TITLE));
        }
    }

    private GHPullRequest getPullRequestWithPullReqIdentifier(GHRepository parent) throws IOException {
        List<GHPullRequest> pullRequests;
        GHUser myself;
        try {
            pullRequests = parent.getPullRequests(GHIssueState.OPEN);
            myself = dockerfileGitHubUtil.getMyself();
        } catch (IOException e) {
            log.warn("Error occurred while retrieving pull requests for {}", parent.getFullName());
            return null;
        }

        for (GHPullRequest pullRequest : pullRequests) {
            GHUser user = pullRequest.getHead().getUser();
            if (myself.equals(user) && pullRequest.getBody().equals(Constants.PULL_REQ_ID)) {
                return pullRequest;
            }
        }
        return null;
    }
}
