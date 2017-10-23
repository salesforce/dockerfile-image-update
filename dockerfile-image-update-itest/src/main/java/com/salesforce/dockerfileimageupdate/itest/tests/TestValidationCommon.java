/*
 * Copyright (c) 2017, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.itest.tests;

import com.salesforce.dockerfileimageupdate.githubutils.GithubUtil;
import org.kohsuke.github.*;
import org.testng.Assert;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.List;

public class TestValidationCommon {
    public static void validateRepo(String repoName, String image, String testTag, GitHub github, GithubUtil githubUtil) throws Exception {
        String login = github.getMyself().getLogin();
        GHRepository repo = githubUtil.tryRetrievingRepository(Paths.get(login, repoName).toString());
        GHContent content = githubUtil.tryRetrievingContent(repo, "Dockerfile", repo.getDefaultBranch());

        try (InputStream stream = content.read(); InputStreamReader streamR = new InputStreamReader(stream);
             BufferedReader reader = new BufferedReader(streamR)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("FROM")) {
                    Assert.assertTrue(line.contains(image), "The command retrieved a repo with a mismatching image.");
                    Assert.assertTrue(line.endsWith(testTag), "The tag has not been changed.");
                }
            }
            validatePullRequestCreation(repo, true);
        }
    }

    public static void validatePullRequestCreation(GHRepository repo, boolean created) throws Exception {
        GHRepository parentRepo = repo.getParent();
        List<GHPullRequest> prs = parentRepo.getPullRequests(GHIssueState.OPEN);
        if (created) {
            Assert.assertEquals(prs.size(), 1, "There should only be one pull request.");
            for (GHPullRequest pr : prs) {
                Assert.assertEquals(pr.listCommits().asList().size(), 1, "More than one commit exists.");
            }
        } else {
            Assert.assertEquals(prs.size(), 0, "There should be no pull requests.");
        }
    }
}
