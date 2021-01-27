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
import com.salesforce.dockerfileimageupdate.model.PullRequestInfo;
import com.salesforce.dockerfileimageupdate.subcommands.ExecutableWithNamespace;
import com.salesforce.dockerfileimageupdate.utils.Constants;
import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;
import net.sourceforge.argparse4j.inf.Namespace;
import org.kohsuke.github.GHRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@SubCommand(help = "updates one specific repository with given tag",
        requiredParams = {Constants.GIT_REPO, Constants.IMG, Constants.FORCE_TAG}, optionalParams = {"s", Constants.STORE})
public class Child implements ExecutableWithNamespace {
    private static final Logger log = LoggerFactory.getLogger(Child.class);

    @Override
    public void execute(final Namespace ns, final DockerfileGitHubUtil dockerfileGitHubUtil)
            throws IOException, InterruptedException {
        String branch = ns.get(Constants.GIT_BRANCH);
        String img = ns.get(Constants.IMG);
        String forceTag = ns.get(Constants.FORCE_TAG);

        /* Updates store if a store is specified. */
        dockerfileGitHubUtil.getGitHubJsonStore(ns.get(Constants.STORE)).updateStore(img, forceTag);

        log.info("Retrieving repository and creating fork...");
        GHRepository repo = dockerfileGitHubUtil.getRepo(ns.get(Constants.GIT_REPO));
        GHRepository fork = dockerfileGitHubUtil.getOrCreateFork(repo);
        if (fork == null) {
            log.info("Unable to fork {}. Please make sure that the repo is forkable.",
                    repo.getFullName());
            return;
        }

        GitForkBranch gitForkBranch = new GitForkBranch(img, forceTag, branch);
        PullRequestInfo pullRequestInfo =
                new PullRequestInfo(ns.get(Constants.GIT_PR_TITLE),
                        gitForkBranch.getImageName(),
                        gitForkBranch.getImageTag(),
                        ns.get(Constants.GIT_PR_BODY));

        dockerfileGitHubUtil.createOrUpdateForkBranchToParentDefault(repo, fork, gitForkBranch);

        log.info("Modifying on Github...");
        dockerfileGitHubUtil.modifyAllOnGithub(fork, gitForkBranch.getBranchName(), img, forceTag);
        dockerfileGitHubUtil.createPullReq(repo,
                gitForkBranch.getBranchName(),
                fork,
                pullRequestInfo);
    }
}
