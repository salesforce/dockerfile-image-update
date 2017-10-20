/*
 * Copyright (c) 2017, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dva.dockerfileimageupdate.itest.tests;

import com.salesforce.dva.dockerfileimageupdate.githubutils.GithubUtil;
import org.kohsuke.github.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Created by minho.park on 8/8/16.
 */
public class AllCommandTest {
    private static final Logger log = LoggerFactory.getLogger(AllCommandTest.class);

    private static final List<String> REPOS = Arrays.asList(
            "dockerfileImageUpdateAllITest1", "dockerfileImageUpdateAllITest2",
            "dockerfileImageUpdateAllITest3", "dockerfileImageUpdateAllITest4");

    private static final List<String> DUPLICATES = Arrays.asList(
            "dockerfileImageUpdateAllITest-Across1", "dockerfileImageUpdateAllITest-Across2");

    private static final List<String> DUPLICATES_CREATED_BY_GIT_HUB = Arrays.asList(
            DUPLICATES.get(0), DUPLICATES.get(0)+"-1", DUPLICATES.get(0)+"-2",
            DUPLICATES.get(1), DUPLICATES.get(1)+"-1", DUPLICATES.get(1)+"-2");

    private static final List<String> ORGS = Arrays.asList(
            "dva-tests", "dva-tests-2", "dva-tests-3");

    private static final String IMAGE_1 = UUID.randomUUID().toString();
    private static final String IMAGE_2 = UUID.randomUUID().toString();
    private static final String TEST_TAG = UUID.randomUUID().toString();
    private static final String STORE_NAME = REPOS.get(0) + "-Store";


    private List<GHRepository> createdRepos = new ArrayList<>();
    private GithubUtil githubUtil; //initialized in setUp
    private GitHub github = null; //initialized in setUp

    @BeforeClass
    public void setUp() throws Exception {
        String gitApiUrl = System.getenv("git_api_url");
        String token = System.getenv("git_api_token");
        github = new GitHubBuilder().withEndpoint(gitApiUrl)
                .withOAuthToken(token)
                .build();
        github.checkApiUrlValidity();
        githubUtil = new GithubUtil(github);

        cleanBefore();

        GHOrganization org = github.getOrganization(ORGS.get(0));
        TestCommon.initializeRepos(org, REPOS, IMAGE_1, createdRepos, githubUtil);

        GHRepository store = github.createRepository(STORE_NAME)
                .description("Delete if this exists. If it exists, then an integration test crashed somewhere.")
                .create();
        store.createContent("{\n  \"images\": {\n" +
                "    \"" + IMAGE_1 + "\": \"" + TEST_TAG + "\",\n" +
                "    \"" + IMAGE_2 + "\": \"" + TEST_TAG + "\"\n" +
                "  }\n" +
                "}",
                "Integration Testing", "store.json");
        createdRepos.add(store);

        for (String s: ORGS) {
            org = github.getOrganization(s);
            TestCommon.initializeRepos(org, DUPLICATES, IMAGE_2, createdRepos, githubUtil);
        }
        /* We need to wait because there is a delay on the search API used in the all command; it takes time
         * for the search API to pick up recently created repositories.
         */
        checkIfSearchUpToDate("image1", IMAGE_1, REPOS.size());
        checkIfSearchUpToDate("image2", IMAGE_2, DUPLICATES.size() * ORGS.size());
    }

    /* We need to wait because there is a delay on the search API used in the all command; it takes time
     * for the search API to pick up recently created repositories.
     */
    private void checkIfSearchUpToDate(String imageName, String image, int numberOfRepos) throws InterruptedException {
        boolean bypassedDelay = false;
        for (int i = 0; i < 60; i++) {
            PagedSearchIterable<GHContent> searchImage1 = github.searchContent().
                    language("Dockerfile").q(image).list();
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

    private void cleanBefore() throws Exception {
        checkAndDeleteBefore(REPOS);
        checkAndDeleteBefore(DUPLICATES_CREATED_BY_GIT_HUB);
    }

    private List<Exception> checkAndDeleteBefore(List<String> repoNames) throws IOException {
        List<Exception> exceptions = new ArrayList<>();
        String user = github.getMyself().getLogin();
        for (String repoName : repoNames) {
            for (String org : ORGS) {
                Exception e1 = checkAndDeleteBefore(Paths.get(user, repoName).toString());
                Exception e2 = checkAndDeleteBefore(Paths.get(org, repoName).toString());
                if (e1 != null) {
                    exceptions.add(e1);
                }
                if (e2 != null) {
                    exceptions.add(e2);
                }
            }

        }
        Exception e3 = checkAndDeleteBefore(Paths.get(user, STORE_NAME).toString());
        if (e3 != null) {
            exceptions.add(e3);
        }
        return exceptions;
    }

    private Exception checkAndDeleteBefore(String repoName) {
        GHRepository repo;
        try {
            repo = github.getRepository(repoName);
        } catch (Exception e) {
            return e;
        }
        try {
            repo.delete();
        } catch (Exception e) {
            return e;
        }
        return null;
    }

    @Test
    public void testAllCommand() throws Exception {
        ProcessBuilder builder = new ProcessBuilder("java", "-jar", "dockerfile-image-update.jar", "all", STORE_NAME);
        builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        Process pc = builder.start(); // may throw IOException

        int exitcode = pc.waitFor();
        Assert.assertEquals(exitcode, 0, "All command for testAllCommand failed.");

        for (String repoName : REPOS) {
            validateRepo(repoName, IMAGE_1);
        }
        for (String repoName : DUPLICATES_CREATED_BY_GIT_HUB) {
            validateRepo(repoName, IMAGE_2);
        }
    }

    @Test(dependsOnMethods = "testAllCommand")
    public void testIdempotency() throws Exception{
        testAllCommand();
    }

    private void validateRepo(String repoName, String image) throws Exception {
        String login = github.getMyself().getLogin();
        GHRepository repo = githubUtil.tryRetrievingRepository(Paths.get(login, repoName).toString());
        GHContent content = githubUtil.tryRetrievingContent(repo, "Dockerfile", repo.getDefaultBranch());

        try (InputStream stream = content.read(); InputStreamReader streamR = new InputStreamReader(stream);
             BufferedReader reader = new BufferedReader(streamR)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("FROM")) {
                    Assert.assertTrue(line.contains(image), "The command retrieved a repo with a mismatching image.");
                    Assert.assertTrue(line.endsWith(TEST_TAG), "The tag has not been changed.");
                }
            }
            validatePullRequestCreation(repo, true);
        }
    }

    private void validatePullRequestCreation(GHRepository repo, boolean created) throws Exception {
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

    @AfterClass
    public void cleanUp() throws Exception {
        List<Exception> exceptions = new ArrayList<>();
        exceptions.addAll(checkAndDelete(createdRepos));
        String login = github.getMyself().getLogin();
        GHRepository storeRepo = github.getRepository(Paths.get(login, STORE_NAME).toString());
        exceptions.add(checkAndDelete(storeRepo));

        for (int i = 0; i < exceptions.size(); i++) {
            log.error("Hit exception {}/{} while cleaning up.", i+1, exceptions.size());
        }
    }

    private List<Exception> checkAndDelete(List<GHRepository> repos) throws IOException {
        List<Exception> exceptions = new ArrayList<>();
        for (GHRepository repo : repos) {
            for (GHRepository fork : repo.listForks()) {
                Exception e1 = checkAndDelete(fork);
                if (e1 != null) {
                    exceptions.add(e1);
                }
            }
            Exception e = checkAndDelete(repo);
            if (e != null) {
                exceptions.add(e);
            }
        }
        return exceptions;
    }

    private Exception checkAndDelete(GHRepository repo) {
        log.info("deleting {}", repo.getFullName());
        try {
            repo.delete();
        } catch (Exception e) {
            return e;
        }
        return null;
    }

}
