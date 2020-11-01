/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.itest.tests;

import com.salesforce.dockerfileimageupdate.itest.MainJarFinder;
import com.salesforce.dockerfileimageupdate.utils.GitHubUtil;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static com.salesforce.dockerfileimageupdate.itest.tests.TestCommon.*;
import static com.salesforce.dockerfileimageupdate.itest.tests.TestValidationCommon.checkIfSearchUpToDate;

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

    private static final String IMAGE_1 = UUID.randomUUID().toString();
    private static final String IMAGE_2 = UUID.randomUUID().toString();
    private static final String TEST_TAG = UUID.randomUUID().toString();
    private static final String STORE_NAME = REPOS.get(0) + "-Store";


    private List<GHRepository> createdRepos = new ArrayList<>();
    private GitHubUtil gitHubUtil; //initialized in setUp
    private GitHub github = null; //initialized in setUp

    @BeforeClass
    public void setUp() throws Exception {
        String gitApiUrl = System.getenv("git_api_url");
        String token = System.getenv("git_api_token");
        github = new GitHubBuilder().withEndpoint(gitApiUrl)
                .withOAuthToken(token)
                .build();
        github.checkApiUrlValidity();
        gitHubUtil = new GitHubUtil(github);

        cleanBefore(REPOS, DUPLICATES_CREATED_BY_GIT_HUB, STORE_NAME, github);

        GHOrganization org = github.getOrganization(ORGS.get(0));
        initializeRepos(org, REPOS, IMAGE_1, createdRepos, gitHubUtil);

        GHRepository store = github.createRepository(STORE_NAME)
                .description("Delete if this exists. If it exists, then an integration test crashed somewhere.")
                .create();
        store.createContent()
                .content("{\n  \"images\": {\n" +
                "    \"" + IMAGE_1 + "\": \"" + TEST_TAG + "\",\n" +
                "    \"" + IMAGE_2 + "\": \"" + TEST_TAG + "\"\n" +
                "  }\n" +
                "}")
                .message("Integration Testing")
                .path("store.json").commit();
        createdRepos.add(store);

        for (String s: ORGS) {
            org = github.getOrganization(s);
            initializeRepos(org, DUPLICATES, IMAGE_2, createdRepos, gitHubUtil);
        }
        /* We need to wait because there is a delay on the search API used in the all command; it takes time
         * for the search API to pick up recently created repositories.
         */
        checkIfSearchUpToDate("image1", IMAGE_1, REPOS.size(), github);
        checkIfSearchUpToDate("image2", IMAGE_2, DUPLICATES.size() * ORGS.size(), github);
    }

    @Test
    public void testAllCommand() throws Exception {
        ProcessBuilder builder = new ProcessBuilder("java", "-jar", MainJarFinder.getName(), "all", STORE_NAME);
        builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        Process pc = builder.start(); // may throw IOException

        int exitcode = pc.waitFor();
        Assert.assertEquals(exitcode, 0, "All command for testAllCommand failed.");

        for (String repoName : REPOS) {
            TestValidationCommon.validateRepo(repoName, IMAGE_1, TEST_TAG, github, gitHubUtil);
        }
        for (String repoName : DUPLICATES_CREATED_BY_GIT_HUB) {
            TestValidationCommon.validateRepo(repoName, IMAGE_2, TEST_TAG, github, gitHubUtil);
        }
    }

    @Test(dependsOnMethods = "testAllCommand")
    public void testIdempotency() throws Exception{
        testAllCommand();
    }

    @AfterClass
    public void cleanUp() throws Exception {
        addVersionStoreRepo(github, createdRepos, STORE_NAME);
        cleanAllRepos(createdRepos, false);
    }

}
