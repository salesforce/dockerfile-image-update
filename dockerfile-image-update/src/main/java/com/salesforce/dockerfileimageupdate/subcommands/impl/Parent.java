/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.subcommands.impl;

import com.google.common.collect.Multimap;
import com.salesforce.dockerfileimageupdate.SubCommand;
import com.salesforce.dockerfileimageupdate.model.GitForkBranch;
import com.salesforce.dockerfileimageupdate.model.GitHubContentToProcess;
import com.salesforce.dockerfileimageupdate.model.PullRequestInfo;
import com.salesforce.dockerfileimageupdate.process.ForkableRepoValidator;
import com.salesforce.dockerfileimageupdate.process.GitHubPullRequestSender;
import com.salesforce.dockerfileimageupdate.subcommands.ExecutableWithNamespace;
import com.salesforce.dockerfileimageupdate.utils.Constants;
import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;
import com.salesforce.dockerfileimageupdate.utils.ResultsProcessor;
import net.sourceforge.argparse4j.inf.Namespace;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedSearchIterable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@SubCommand(help="updates all repositories' Dockerfiles with given base image",
        requiredParams = {Constants.IMG, Constants.TAG, Constants.STORE})

public class Parent implements ExecutableWithNamespace {

    private static final Logger log = LoggerFactory.getLogger(Parent.class);

    DockerfileGitHubUtil dockerfileGitHubUtil;

    @Override
    public void execute(final Namespace ns, DockerfileGitHubUtil dockerfileGitHubUtil)
            throws IOException, InterruptedException {
        loadDockerfileGithubUtil(dockerfileGitHubUtil);
        String img = ns.get(Constants.IMG);
        String tag = ns.get(Constants.TAG);

        log.info("Updating store...");
        this.dockerfileGitHubUtil.getGitHubJsonStore(ns.get(Constants.STORE)).updateStore(img, tag);

        GitHubPullRequestSender pullRequestSender =
                new GitHubPullRequestSender(dockerfileGitHubUtil, new ForkableRepoValidator(dockerfileGitHubUtil),
                        ns.get(Constants.GIT_REPO_EXCLUDES));

        GitForkBranch gitForkBranch =
                new GitForkBranch(ns.get(Constants.IMG), ns.get(Constants.TAG), ns.get(Constants.GIT_BRANCH));

        log.info("Finding Dockerfiles with the given image...");
        Optional<PagedSearchIterable<GHContent>> contentsWithImage = dockerfileGitHubUtil.getGHContents(ns.get(Constants.GIT_ORG), img);
        if (contentsWithImage.isPresent()) {
            Multimap<String, GitHubContentToProcess> pathToDockerfilesInParentRepo =
                    pullRequestSender.forkRepositoriesFoundAndGetPathToDockerfiles(contentsWithImage.get(), gitForkBranch);
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

            ResultsProcessor.processResults(skippedRepos, exceptions, log);
        }
    }

    protected void loadDockerfileGithubUtil(DockerfileGitHubUtil _dockerfileGitHubUtil) {
        dockerfileGitHubUtil = _dockerfileGitHubUtil;
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
            PullRequestInfo pullRequestInfo =
                    new PullRequestInfo(ns.get(Constants.GIT_PR_TITLE),
                            gitForkBranch.getImageName(),
                            gitForkBranch.getImageTag(),
                            ns.get(Constants.GIT_PR_BODY));
            // TODO: get the new PR number and cross post over to old ones
            dockerfileGitHubUtil.createPullReq(parent,
                    gitForkBranch.getBranchName(),
                    forkedRepo,
                    pullRequestInfo);
            // TODO: Run through PRs in fork to see if they have head branches that match the prefix and close those?
        }
    }
}
