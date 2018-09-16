/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.itest.tests;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.salesforce.dockerfileimageupdate.utils.GitHubUtil;
import org.kohsuke.github.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.salesforce.dockerfileimageupdate.itest.tests.TestCommon.ORGS;
import static com.salesforce.dockerfileimageupdate.itest.tests.TestCommon.addVersionStoreRepo;

/**
 * Created by minho.park on 7/14/16.
 */
public class ChildCommandTest {
    private static final Logger log = LoggerFactory.getLogger(ChildCommandTest.class);

    private static final String NAME = "dockerfileImageUpdateChildITest";
    private static final String IMAGE = UUID.randomUUID().toString();
    private static final String TAG = UUID.randomUUID().toString();
    private static final String STORE_NAME = NAME + "-store";
    private static final String ORG = ORGS.get(0);

    // The following are initialized in setup
    private List<GHRepository> createdRepos = new ArrayList<>();
    private GitHub github = null;
    private GitHubUtil gitHubUtil;

    @BeforeClass
    public void setUp() throws Exception {
        String gitApiUrl = System.getenv("git_api_url");
        String token = System.getenv("git_api_token");
        github = new GitHubBuilder().withEndpoint(gitApiUrl)
                .withOAuthToken(token)
                .build();
        github.checkApiUrlValidity();
        cleanBefore();

        gitHubUtil = new GitHubUtil(github);
        GHOrganization org = github.getOrganization(ORG);

        GHRepository repo = org.createRepository(NAME)
                .description("Delete if this exists. If it exists, then an integration test crashed somewhere.")
                .private_(false)
                .create();
        log.info("Initializing {}", repo.getFullName());
        createdRepos.add(repo);
        repo.createContent("FROM " + IMAGE + ":test", "Integration Testing", "Dockerfile");
    }

    @Test
    public void testChild() throws Exception {
        ProcessBuilder builder = new ProcessBuilder("java", "-jar", "dockerfile-image-update.jar", "child",
                Paths.get(ORG, NAME).toString(), IMAGE, TAG);
        builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        Process pc = builder.start();

        int exitcode = pc.waitFor();
        Assert.assertEquals(exitcode, 0);

        TestValidationCommon.validateRepo(NAME, IMAGE, TAG, github, gitHubUtil);
    }

    @Test(dependsOnMethods = "testChild")
    public void testIdempotency() throws Exception{
        testChild();
    }

    @Test(dependsOnMethods = "testIdempotency")
    public void testStoreUpdate() throws Exception {
        ProcessBuilder builder = new ProcessBuilder("java", "-jar", "dockerfile-image-update.jar", "child",
                Paths.get(ORG, NAME).toString(), IMAGE, TAG, "-s", STORE_NAME);
        builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        Process pc = builder.start();

        int exitcode = pc.waitFor();
        Assert.assertEquals(exitcode, 0);

        JsonElement json;
        JsonElement images = null;
        for (int i = 0; i < 5; i++) {
            String user = github.getMyself().getLogin();
            GHRepository storeRepo = github.getRepository(Paths.get(user, STORE_NAME).toString());
            GHContent store = storeRepo.getFileContent("store.json");
            try (InputStream stream = store.read(); InputStreamReader streamR = new InputStreamReader(stream)) {
                    json = new JsonParser().parse(streamR);
                    images = json.getAsJsonObject().get("images");
                    break;
            } catch (IllegalStateException e) {
                /* Sometimes the contents aren't created yet, so we wait. */
                log.warn("Content in store not created yet. Trying again...");
                Thread.sleep(TimeUnit.SECONDS.toMillis(1));
            }
        }
        if (images == null) {
            Assert.fail();
        } else {
            Assert.assertEquals(images.getAsJsonObject().get(IMAGE).getAsString(), TAG);
        }
    }

    @Test(dependsOnMethods = "testStoreUpdate")
    public void testAsUser() throws Exception {
        GHRepository repo = github.getOrganization(ORG).getRepository(NAME);
        List<GHPullRequest> prs = repo.getPullRequests(GHIssueState.OPEN);
        Assert.assertTrue(prs.size() == 1);
        for (GHPullRequest pr : prs) {
            pr.merge("Automatic merge through itests.");
            pr.close();
        }

        try (InputStream stream = repo.getFileContent("Dockerfile").read();
             InputStreamReader streamR = new InputStreamReader(stream);
             BufferedReader reader = new BufferedReader(streamR)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("FROM")) {
                    Assert.assertTrue(line.contains(IMAGE));
                    Assert.assertTrue(line.endsWith(TAG));
                }
            }
        }
    }

    private void cleanBefore() throws Exception {
        String login = github.getMyself().getLogin();
        List<String> repoNames = Arrays.asList(
                Paths.get(login, NAME).toString(),
                Paths.get(ORG, NAME).toString(),
                Paths.get(login, STORE_NAME).toString());
        for (String repoName : repoNames) {
            TestCommon.checkAndDeleteBefore(repoName, github);
        }
    }

    @AfterClass
    public void cleanUp() throws Exception {
        addVersionStoreRepo(github, createdRepos, STORE_NAME);
        TestCommon.cleanAllRepos(createdRepos, false);
    }
}
