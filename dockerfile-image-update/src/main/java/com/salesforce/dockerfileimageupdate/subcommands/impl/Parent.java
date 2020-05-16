/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.subcommands.impl;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.salesforce.dockerfileimageupdate.SubCommand;
import com.salesforce.dockerfileimageupdate.model.GitForkBranch;
import com.salesforce.dockerfileimageupdate.model.GitHubContentToProcess;
import com.salesforce.dockerfileimageupdate.subcommands.ExecutableWithNamespace;
import com.salesforce.dockerfileimageupdate.utils.Constants;
import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;
import net.sourceforge.argparse4j.inf.Namespace;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedSearchIterable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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
        PagedSearchIterable<GHContent> contentsWithImage = getGHContents(ns.get(Constants.GIT_ORG), img);
        if (contentsWithImage == null) return;

        Multimap<String, GitHubContentToProcess> pathToDockerfilesInParentRepo = forkRepositoriesFoundAndGetPathToDockerfiles(contentsWithImage);
        List<IOException> exceptions = new ArrayList<>();
        List<String> skippedRepos = new ArrayList<>();

        for (String currUserRepo : pathToDockerfilesInParentRepo.keySet()) {
            Optional<GitHubContentToProcess> forkWithContentPaths =
                    pathToDockerfilesInParentRepo.get(currUserRepo).stream().findFirst();
            if (forkWithContentPaths.isPresent()) {
                try {
                    changeDockerfiles(ns, pathToDockerfilesInParentRepo, forkWithContentPaths.get(), skippedRepos);
                } catch (IOException e) {
                    log.error(String.format("Error changing Dockerfile for %s", forkWithContentPaths.get().getParent().getFullName()), e);
                    exceptions.add(e);
                }
            } else {
                log.warn("Didn't find fork for {} so not changing Dockerfiles", currUserRepo);
            }
        }
        if (!exceptions.isEmpty()) {
            throw new IOException(String.format("There were %s errors with changing Dockerfiles.", exceptions.size()));
        }

        if (!skippedRepos.isEmpty()) {
            log.info("List of repos skipped: {}", skippedRepos);
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
    protected Multimap<String, GitHubContentToProcess> forkRepositoriesFoundAndGetPathToDockerfiles(PagedSearchIterable<GHContent> contentsWithImage) throws IOException {
        log.info("Forking repositories...");
        Multimap<String, GitHubContentToProcess> pathToDockerfilesInParentRepo = HashMultimap.create();
        GHRepository parent;
        String parentRepoName;
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
            // TODO: Error check... Refresh the repo to ensure that the object has full details
            parent = dockerfileGitHubUtil.getRepo(parentRepoName);
            if (parent.isFork()) {
                log.warn("Skipping {} because it's a fork already. Sending a PR to a fork is unsupported at the moment.",
                        parentRepoName);
            } else if (parent.isArchived()) {
                log.warn("Skipping {} because it's archived.", parent.getFullName());
            } else if (dockerfileGitHubUtil.thisUserIsOwner(parent)) {
                log.warn("Skipping {} because it is owned by this user.", parent.getFullName());
            } else {
                // fork the parent if not already forked
                GHRepository fork;
                if (pathToDockerfilesInParentRepo.containsKey(parentRepoName)) {
                    // Found more content for this fork, so add it as well
                    Collection<GitHubContentToProcess> gitHubContentToProcesses = pathToDockerfilesInParentRepo.get(parentRepoName);
                    Optional<GitHubContentToProcess> firstForkData = gitHubContentToProcesses.stream().findFirst();
                    if (firstForkData.isPresent()) {
                        fork = firstForkData.get().getFork();
                        pathToDockerfilesInParentRepo.put(parentRepoName, new GitHubContentToProcess(fork, parent, c.getPath()));
                    } else {
                        log.warn("For some reason we have ");
                    }
                } else {
                    log.info("Getting or creating fork: {}", parentRepoName);
                    fork = dockerfileGitHubUtil.getOrCreateFork(parent);
//                    fork = null;
                    if (fork == null) {
                        log.info("Could not fork {}", parentRepoName);
                    } else {
                        // Add repos to pathToDockerfilesInParentRepo only if we forked it successfully.
                        pathToDockerfilesInParentRepo.put(parentRepoName, new GitHubContentToProcess(fork, parent, c.getPath()));
                    }
                }
            }
        }

        log.info("Path to Dockerfiles in repos: {}", pathToDockerfilesInParentRepo);

        return pathToDockerfilesInParentRepo;
    }

    protected void changeDockerfiles(Namespace ns,
                                     Multimap<String, GitHubContentToProcess> pathToDockerfilesInParentRepo,
                                     GitHubContentToProcess gitHubContentToProcess,
                                     List<String> skippedRepos) throws IOException,
            InterruptedException {
        // Should we skip doing a getRepository just to fill in the parent value? We already know this to be the parent...
        GHRepository parent = gitHubContentToProcess.getParent();
        GHRepository forkedRepo = gitHubContentToProcess.getFork();
        // TODO: Getting a null pointer here for someone... probably just fixed this since we have parent
        String parentName = parent.getFullName();

        log.info("Fixing Dockerfiles in {} to PR to {}", forkedRepo.getFullName(), parent.getFullName());
        GitForkBranch gitForkBranch = new GitForkBranch(ns.get(Constants.IMG), ns.get(Constants.TAG), ns.get(Constants.GIT_BRANCH));

        dockerfileGitHubUtil.createOrUpdateForkBranchToParentDefault(parent, forkedRepo, gitForkBranch);

        // loop through all the Dockerfiles in the same repo
        boolean isContentModified = false;
        boolean isRepoSkipped = true;
        for (GitHubContentToProcess forkWithCurrentContentPath : pathToDockerfilesInParentRepo.get(parentName)) {
            String pathToDockerfile = forkWithCurrentContentPath.getContentPath();
            GHContent content = dockerfileGitHubUtil.tryRetrievingContent(forkedRepo, pathToDockerfile, gitForkBranch.getBranchName());
            if (content == null) {
                log.info("No Dockerfile found at path: '{}'", pathToDockerfile);
            } else {
                dockerfileGitHubUtil.modifyOnGithub(content, gitForkBranch.getBranchName(), gitForkBranch.getImageName(), gitForkBranch.getImageTag(),
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
            // TODO: get the new PR number and cross post over to old ones
            dockerfileGitHubUtil.createPullReq(parent, gitForkBranch.getBranchName(), forkedRepo, ns.get(Constants.GIT_PR_TITLE));
            // TODO: Run through PRs in fork to see if they have head branches that match the prefix and close those?
        }
    }
    private Optional<GHPullRequest> commentAndCloseExistingPullRequestsForBranch(GHRepository parentRepo, GitForkBranch gitForkBranch) {
        Optional<GHPullRequest> specifiedBranchPullRequest = dockerfileGitHubUtil.getPullRequestForImageBranch(parentRepo, gitForkBranch);
        //            GHPullRequest pullRequest = specifiedBranchPullRequest.get();
        //            try {
        //                pullRequest.comment(String.format("Closing PR due to PR update for image %s and tag %s",
        //                        gitForkBranch.getImageName(), gitForkBranch.getImageTag()));
        //                pullRequest.close();
        //                log.info("Found and closed PR {}", pullRequest.getUrl());
        //            } catch (IOException ioException) {
        //                log.error("Error trying to comment/close PR for {}: {}", pullRequest.getUrl(), ioException.getMessage());
        //            }
        specifiedBranchPullRequest.ifPresent(ghPullRequest -> log.info("yeah... going to do the things on {}", ghPullRequest.getHtmlUrl()));
        return specifiedBranchPullRequest;
    }

}
