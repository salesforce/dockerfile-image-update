/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.subcommands.impl;

import com.google.common.collect.Multimap;
import com.google.gson.JsonElement;
import com.salesforce.dockerfileimageupdate.SubCommand;
import com.salesforce.dockerfileimageupdate.model.*;
import com.salesforce.dockerfileimageupdate.process.*;
import com.salesforce.dockerfileimageupdate.subcommands.ExecutableWithNamespace;
import com.salesforce.dockerfileimageupdate.utils.Constants;
import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;
import com.salesforce.dockerfileimageupdate.utils.ResultsProcessor;
import com.salesforce.dockerfileimageupdate.subcommands.commonsteps.Common;
import net.sourceforge.argparse4j.inf.Namespace;
import org.kohsuke.github.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

@SubCommand(help="updates all repositories' Dockerfiles",
        requiredParams = {Constants.STORE})
public class All implements ExecutableWithNamespace {
    private static final Logger log = LoggerFactory.getLogger(All.class);

    private DockerfileGitHubUtil dockerfileGitHubUtil;

    @Override
    public void execute(final Namespace ns, final DockerfileGitHubUtil dockerfileGitHubUtil)
            throws IOException, InterruptedException {
        loadDockerfileGithubUtil(dockerfileGitHubUtil);
        Set<Map.Entry<String, JsonElement>> imageToTagStore =
                this.dockerfileGitHubUtil.getGitHubJsonStore(ns.get(Constants.STORE)).parseStoreToImagesMap(dockerfileGitHubUtil, ns.get(Constants.STORE));
        Integer gitApiSearchLimit = ns.get(Constants.GIT_API_SEARCH_LIMIT);
        Map<String, Boolean> orgsToIncludeInSearch = new HashMap<>();
        if (ns.get(Constants.GIT_ORG) != null) {
            // If there is a Git org specified, that needs to be included in the search query. In
            // the orgsToIncludeInSearch a true value associated with an org name ensures that
            // the org gets included in the search query.
            orgsToIncludeInSearch.put(ns.get(Constants.GIT_ORG), true);
        }
        for (Map.Entry<String, JsonElement> imageToTag : imageToTagStore) {
            String image = imageToTag.getKey();
            String tag = imageToTag.getValue().getAsString();
            Common commonSteps = getCommon();
            GitHubPullRequestSender pullRequestSender = getPullRequestSender(dockerfileGitHubUtil, ns);
            GitForkBranch gitForkBranch = getGitForkBranch(image, tag, ns);

            log.info("Finding Dockerfiles with the image name {}...", image);

            Optional<List<PagedSearchIterable<GHContent>>> contentsWithImage =
                    this.dockerfileGitHubUtil.findFilesWithImage(image, orgsToIncludeInSearch, gitApiSearchLimit);

            if (contentsWithImage.isPresent()) {
                contentsWithImage.get().forEach(pagedSearchIterable -> {
                    try {
                        commonSteps.prepareToCreatePullRequests(ns, pullRequestSender,
                                pagedSearchIterable, gitForkBranch, dockerfileGitHubUtil);
                    } catch (IOException e) {
                        log.error("Could not send pull request.");
                    }
                });
            }
        }
    }

    protected void loadDockerfileGithubUtil(DockerfileGitHubUtil _dockerfileGitHubUtil) {
        dockerfileGitHubUtil = _dockerfileGitHubUtil;
    }

    protected GitHubPullRequestSender getPullRequestSender(DockerfileGitHubUtil dockerfileGitHubUtil, Namespace ns){
        return new GitHubPullRequestSender(dockerfileGitHubUtil, new ForkableRepoValidator(dockerfileGitHubUtil),
                ns.get(Constants.GIT_REPO_EXCLUDES));
    }

    protected GitForkBranch getGitForkBranch(String image, String tag, Namespace ns){
        return new GitForkBranch(image, tag, ns.get(Constants.GIT_BRANCH));
    }

    protected Common getCommon(){
        return new Common();
    }
}
