/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.subcommands.impl;


import com.salesforce.dockerfileimageupdate.SubCommand;
import com.salesforce.dockerfileimageupdate.model.GitForkBranch;
import com.salesforce.dockerfileimageupdate.process.ForkableRepoValidator;
import com.salesforce.dockerfileimageupdate.process.GitHubPullRequestSender;
import com.salesforce.dockerfileimageupdate.storage.GitHubJsonStore;
import com.salesforce.dockerfileimageupdate.subcommands.ExecutableWithNamespace;
import com.salesforce.dockerfileimageupdate.utils.*;
import net.sourceforge.argparse4j.inf.Namespace;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.PagedSearchIterable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@SubCommand(help="updates all repositories' Dockerfiles with given base image",
        requiredParams = {Constants.IMG, Constants.TAG, Constants.STORE})

public class Parent implements ExecutableWithNamespace {

    private static final Logger log = LoggerFactory.getLogger(Parent.class);
    private static final String s3Prefix = "s3://";

    DockerfileGitHubUtil dockerfileGitHubUtil;
    DockerfileS3Util dockerfileS3Util;

    @Override
    public void execute(final Namespace ns, DockerfileGitHubUtil dockerfileGitHubUtil, DockerfileS3Util dockerfileS3Util)
            throws IOException, InterruptedException {
        loadDockerfileGithubUtil(dockerfileGitHubUtil);
        loadDockerfileS3Util(dockerfileS3Util);
        String img = ns.get(Constants.IMG);
        String tag = ns.get(Constants.TAG);

        log.info("Updating store...");
        String store = ns.get(Constants.STORE);
        if (store.startsWith(s3Prefix)) {
            log.info("Using S3 bucket as the underlying data store");
            this.dockerfileS3Util.getS3ImageStore(store).updateS3Store(img, tag);
        } else {
            log.info("Using Git repo as the underlying data store");
            this.dockerfileGitHubUtil.getGitHubJsonStore(store).updateStore(img, tag);
        }


        if (ns.get(Constants.SKIP_PR_CREATION)) {
            log.info("Since the flag {} is set to True, the PR creation steps will "
                    + "be skipped.", Constants.SKIP_PR_CREATION);
            return;
        }
        PullRequests pullRequests = getPullRequests();
        GitHubPullRequestSender pullRequestSender = getPullRequestSender(dockerfileGitHubUtil, ns);
        GitForkBranch gitForkBranch = getGitForkBranch(ns);
        log.info("Finding Dockerfiles with the given image...");

        Integer gitApiSearchLimit = ns.get(Constants.GIT_API_SEARCH_LIMIT);
        Optional<List<PagedSearchIterable<GHContent>>> contentsWithImage = dockerfileGitHubUtil.getGHContents(ns.get(Constants.GIT_ORG), img, gitApiSearchLimit);

        if (contentsWithImage.isPresent()) {
            List<PagedSearchIterable<GHContent>> contentsFoundWithImage = contentsWithImage.get();
            for (int i = 0; i < contentsFoundWithImage.size(); i++ ) {
                try {
                    pullRequests.prepareToCreate(ns, pullRequestSender,
                            contentsFoundWithImage.get(i), gitForkBranch, dockerfileGitHubUtil);
                } catch (IOException e) {
                    log.error("Could not send pull request.", e);
                }
            }
        }
    }

    protected PullRequests getPullRequests(){
        return new PullRequests();
    }

    protected GitForkBranch getGitForkBranch(Namespace ns){
        return new GitForkBranch(ns.get(Constants.IMG), ns.get(Constants.TAG), ns.get(Constants.GIT_BRANCH));
    }

    protected GitHubPullRequestSender getPullRequestSender(DockerfileGitHubUtil dockerfileGitHubUtil, Namespace ns){
        return new GitHubPullRequestSender(dockerfileGitHubUtil, new ForkableRepoValidator(dockerfileGitHubUtil),
                ns.get(Constants.GIT_REPO_EXCLUDES));
    }

    protected void loadDockerfileGithubUtil(DockerfileGitHubUtil _dockerfileGitHubUtil) {
        dockerfileGitHubUtil = _dockerfileGitHubUtil;
    }

    protected void loadDockerfileS3Util(DockerfileS3Util _dockerfileS3UtilUtil) {
        dockerfileS3Util = _dockerfileS3UtilUtil;
    }
}
