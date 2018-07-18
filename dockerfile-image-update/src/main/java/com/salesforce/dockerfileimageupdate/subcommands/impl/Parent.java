/*
 * Copyright (c) 2017, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.subcommands.impl;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.salesforce.dockerfileimageupdate.SubCommand;
import com.salesforce.dockerfileimageupdate.subcommands.ExecutableWithNamespace;
import com.salesforce.dockerfileimageupdate.utils.Constants;
import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;
import net.sourceforge.argparse4j.inf.Namespace;
import org.kohsuke.github.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@SubCommand(help="updates all repositories' Dockerfiles with given base image",
        requiredParams = {Constants.IMG, Constants.TAG, Constants.STORE})

public class Parent implements ExecutableWithNamespace {
    private static final Logger log = LoggerFactory.getLogger(Parent.class);

    private DockerfileGitHubUtil dockerfileGitHubUtil;

    @Override
    public void execute(final Namespace ns, DockerfileGitHubUtil dockerfileGitHubUtil)
            throws IOException, InterruptedException {
        loadDockerfileGithubUtil(dockerfileGitHubUtil);
        String img = ns.get(Constants.IMG);
        String tag = ns.get(Constants.TAG);

        log.info("Updating store...");
        this.dockerfileGitHubUtil.updateStore(ns.get(Constants.STORE), img, tag);

        log.info("Finding Dockerfiles with the given image...");
        log.info("org: {}", (String) ns.get("o"));
        PagedSearchIterable<GHContent> contentsWithImage = getGHContents(ns.get("o"), img);
        if (contentsWithImage == null) return;

        Multimap<String, String> pathToDockerfilesInParentRepo = forkRepositoriesFoundAndGetPathToDockerfiles(contentsWithImage);

        GHMyself currentUser = this.dockerfileGitHubUtil.getMyself();
        if (currentUser == null) {
            throw new IOException("Could not retrieve authenticated user.");
        }

        PagedIterable<GHRepository> listOfcurrUserRepos =
                dockerfileGitHubUtil.getGHRepositories(pathToDockerfilesInParentRepo, currentUser);

        List<IOException> exceptions = new ArrayList<>();
        List<String> skippedRepos = new ArrayList<>();

        for (GHRepository currUserRepo : listOfcurrUserRepos) {
            try {
                log.info("userRepo: {}", currUserRepo.getFullName());
                changeDockerfiles(ns, pathToDockerfilesInParentRepo, currUserRepo, skippedRepos);
            } catch (IOException e) {
                exceptions.add(e);
            }
        }
        if (!exceptions.isEmpty()) {
            log.info("There were {} errors with changing Dockerfiles.", exceptions.size());
            throw exceptions.get(0);
        }

        if (!skippedRepos.isEmpty()) {
            log.info("List of repos skipped: {}", skippedRepos.toArray());
        }
    }

    protected void loadDockerfileGithubUtil(DockerfileGitHubUtil _dockerfileGitHubUtil) {
        dockerfileGitHubUtil = _dockerfileGitHubUtil;
    }

    protected PagedSearchIterable<GHContent> getGHContents(String org, String img)
            throws IOException, InterruptedException {
        PagedSearchIterable<GHContent> contentsWithImage = null;
        for (int i = 0; i < 5; i++) {
            contentsWithImage = dockerfileGitHubUtil.findFilesWithImage(img, org);
            if (contentsWithImage.getTotalCount() > 0) {
                break;
            } else {
                Thread.sleep(1000);
            }
        }

        int numOfContentsFound = contentsWithImage.getTotalCount();
        log.info("numOfContentsFound: {}", numOfContentsFound);
        if (numOfContentsFound <= 0) {
            log.info("Could not find any repositories with given image.");
            return null;
        }
        return contentsWithImage;
    }

    /* There is a separation here with forking and performing the Dockerfile update. This is because of the delay
     * on Github, where after the fork, there may be a time gap between repository creation and content replication
     * when forking. So, in hopes of alleviating the situation a little bit, we do all the forking before the
     * Dockerfile updates.
     *
     * NOTE: We are not currently forking repositories that are already forks
     */
    protected Multimap<String, String> forkRepositoriesFoundAndGetPathToDockerfiles(PagedSearchIterable<GHContent> contentsWithImage) throws IOException {
        log.info("Forking repositories...");
        Multimap<String, String> pathToDockerfilesInParentRepo = HashMultimap.create();
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
                pathToDockerfilesInParentRepo.put(c.getOwner().getFullName(), c.getPath());
                // fork the parent if not already forked
                if (!parentReposForked.contains(parentRepoName)) {
                    log.info("Forking {}", parentRepoName);
                    dockerfileGitHubUtil.closeOutdatedPullRequestAndForkParent(parent);
                    parentReposForked.add(parentRepoName);
                }
            }
        }

        log.info("Path to Dockerfiles in repo '{}': {}", parentRepoName, pathToDockerfilesInParentRepo.toString());

        return pathToDockerfilesInParentRepo;
    }

    protected void changeDockerfiles(Namespace ns,
                                     Multimap<String, String> pathToDockerfilesInParentRepo,
                                     GHRepository currUserRepo,
                                     List<String> skippedRepos) throws IOException,
            InterruptedException {
        /* The Github API does not provide the parent if retrieved through a list. If we want to access its parent,
         * we need to retrieve it once again.
         */
        GHRepository forkedRepo;
        if (currUserRepo.isFork()) {
            log.info("Re-retrieving repo {}", currUserRepo.getFullName());
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

        if (parent == null || !pathToDockerfilesInParentRepo.containsKey(parent.getFullName())) {
            return;
        }
        log.info("Fixing Dockerfiles in {}", forkedRepo.getFullName());
        String parentName = parent.getFullName();
        String branch = (ns.get("b") == null) ? forkedRepo.getDefaultBranch() : ns.get("b");

        // loop through all the Dockerfiles in the same repo
        boolean isContentModified = false;
        boolean isRepoSkipped = true;
        for (String pathToDockerfile : pathToDockerfilesInParentRepo.get(parentName)) {
            GHContent content = dockerfileGitHubUtil.tryRetrievingContent(forkedRepo, pathToDockerfile, branch);
            log.info("content: {}", content);
            if (content != null) {
                dockerfileGitHubUtil.modifyOnGithub(content, branch, ns.get(Constants.IMG), ns.get(Constants.TAG), ns.get("c"));
                isContentModified = true;
                isRepoSkipped = false;
            } else {
                log.info("No Dockerfile found at path: '{}'", pathToDockerfile);
            }
        }

        if (isRepoSkipped) {
            log.info("Skipping repo '{}' because contents of it's fork could not be retrieved. Moving ahead...",
                    parentName);
            skippedRepos.add(forkedRepo.getFullName());
        }

        if (isContentModified) {
            dockerfileGitHubUtil.createPullReq(parent, branch, forkedRepo, ns.get("m"));
        }
    }
}
