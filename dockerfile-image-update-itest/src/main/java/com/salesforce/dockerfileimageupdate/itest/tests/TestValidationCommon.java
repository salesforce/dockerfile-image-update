/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.itest.tests;

import com.salesforce.dockerfileimageupdate.utils.GitHubUtil;
import org.apache.commons.io.IOUtils;
import org.assertj.core.api.Assertions;
import org.kohsuke.github.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestValidationCommon {
    private static final Logger log = LoggerFactory.getLogger(TestValidationCommon.class);
    private static final long MAX_CONTENT_SIZE = 512L * 1024;

    public static void validateRepo(String repoName, String image, String testTag, GitHub github, GitHubUtil gitHubUtil) throws Exception {
        String login = github.getMyself().getLogin();
        // Github is eventually consistent, give it some time to read
        for (int attempt = 0; attempt < 5; attempt++) {
            GHRepository repo = gitHubUtil.tryRetrievingRepository(Paths.get(login, repoName).toString());
            if (repo == null) {
                fail("Repository not found: " + Paths.get(login, repoName).toString());
            }
            String latestCommit = repo.getBranches().get(repo.getDefaultBranch()).getSHA1();
            GHContent content = gitHubUtil.tryRetrievingContent(repo, "Dockerfile", latestCommit);
            if (content.getSize() > MAX_CONTENT_SIZE) {
                fail(String.format("Content is suspiciously large: %s, should be below %s", content.getSize(), MAX_CONTENT_SIZE));
            }
            String dockerfileContent = IOUtils.toString(content.read());
            if (dockerfileContent.contains(testTag)) {
                assertThat(dockerfileContent).startsWith("FROM");
                assertThat(dockerfileContent).contains(image);
                assertThat(dockerfileContent).contains(testTag);
                validatePullRequestCreation(repo, true);
                return;
            }
            log.info("Dockerfile (commitref: {}, contents: {}) in {} did not contain tag {}, try #{}",
                    latestCommit, dockerfileContent, repo.getFullName(), testTag, attempt);
            Thread.sleep(TimeUnit.SECONDS.toMillis(1));
        }
        fail(String.format("Didn't find tag (%s) in Dockerfile in repo (%s)", testTag, repoName));
    }

    public static void validatePullRequestCreation(GHRepository repo, boolean created) throws Exception {
        GHRepository parentRepo = repo.getParent();
        List<GHPullRequest> prs = parentRepo.getPullRequests(GHIssueState.OPEN);
        if (created) {
            assertEquals(prs.size(), 1, "There should only be one pull request.");
            for (GHPullRequest pr : prs) {
                // TODO: In sometimes two commits are generated; a blank one followed tag bump. Ideally we'd have one
                Assertions.assertThat(pr.listCommits().asList().size()).isGreaterThanOrEqualTo(1);
            }
        } else {
            assertEquals(prs.size(), 0, "There should be no pull requests.");
        }
    }

    /* We need to wait because there is a delay on the search API used in the all command; it takes time
     * for the search API to pick up recently created repositories.
     */
    public static void checkIfSearchUpToDate(String imageName, String image, int numberOfRepos, GitHub github)
            throws InterruptedException {
        boolean bypassedDelay = false;
        for (int i = 0; i < 60; i++) {
            PagedSearchIterable<GHContent> searchImage1 = github.searchContent().
                    filename("Dockerfile").q(image).list();
            log.info("Currently {} search gives {} results. It should be {}.", imageName,
                    searchImage1.getTotalCount(), numberOfRepos);
            if (searchImage1.getTotalCount() >= 4) {
                bypassedDelay = true;
                break;
            } else {
                /* Arbitrary 3 seconds to space out the API search calls. */
                Thread.sleep(TimeUnit.SECONDS.toMillis(3));
            }
        }
        if (!bypassedDelay) {
            log.error("Failed to initialize.");
        }
    }
}
