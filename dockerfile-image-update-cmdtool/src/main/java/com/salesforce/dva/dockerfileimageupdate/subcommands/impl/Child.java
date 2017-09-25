/*
 * Copyright (c) 2017, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dva.dockerfileimageupdate.subcommands.impl;

import com.salesforce.dva.dockerfileimageupdate.SubCommand;
import com.salesforce.dva.dockerfileimageupdate.subcommands.ExecutableWithNamespace;
import com.salesforce.dva.dockerfileimageupdate.utils.DockerfileGithubUtil;
import net.sourceforge.argparse4j.inf.Namespace;
import org.kohsuke.github.GHRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static com.salesforce.dva.dockerfileimageupdate.utils.Constants.*;

@SubCommand(help="updates one specific repository with given tag",
        requiredParams = {GIT_REPO, IMG, FORCE_TAG}, optionalParams = {"s", STORE})
public class Child implements ExecutableWithNamespace {
    private final static Logger log = LoggerFactory.getLogger(Child.class);

    @Override
    public void execute(final Namespace ns, final DockerfileGithubUtil dockerfileGithubUtil)
            throws IOException, InterruptedException {
        String branch = ns.get("b");
        String img = ns.get(IMG);
        String forceTag = ns.get(FORCE_TAG);

        /* Updates store if a store is specified. */
        dockerfileGithubUtil.updateStore(ns.get(STORE), img, forceTag);

        log.info("Retrieving repository and creating fork...");
        GHRepository repo = dockerfileGithubUtil.getRepo(ns.get(GIT_REPO));
        GHRepository fork = dockerfileGithubUtil.checkFromParentAndFork(repo);

        if (branch == null) {
            branch = repo.getDefaultBranch();
        }
        log.info("Modifying on Github...");
        dockerfileGithubUtil.modifyAllOnGithub(fork, branch, img, forceTag);

        String message = ns.get("m");


        dockerfileGithubUtil.createPullReq(repo, branch, fork, message);

        /* TODO: A potential problem that requires a design decision:
         * 1. Leave forks in authenticated repository.
         *    Pro: If the pull request has not been merged, it won't create a new pull request.
         *    Con: Makes a lot of fork repositories on personal repository.
         * 2. Delete forks in authenticated repository.
         *    Pro: No extra repositories on personal repository; authenticated repository stays clean.
         *    Con: If the pull request not merged yet, it will create a new pull request, making it harder for users
         *         to read.
         */
//        fork.delete();
    }
}
