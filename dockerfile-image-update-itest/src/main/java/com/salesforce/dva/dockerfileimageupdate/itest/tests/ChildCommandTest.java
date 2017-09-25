package com.salesforce.dva.dockerfileimageupdate.itest.tests;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
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
 * Created by minho.park on 7/14/16.
 */
public class ChildCommandTest {
    private static final Logger log = LoggerFactory.getLogger(ChildCommandTest.class);

    private static final String NAME = "dockerfileImageUpdateChildITest";
    private static final String IMAGE = UUID.randomUUID().toString();
    private static final String TAG = UUID.randomUUID().toString();
    private static final String STORE_NAME = NAME + "-store";
    private static final String ORG = "dva-tests";
    private List<GHRepository> createdRepos = new ArrayList<>();
    private GitHub github = null;


    @Test
    public void testExample() {
        Assert.assertTrue(true);
    }

    @BeforeClass
    public void setUp() throws Exception {
        String gitApiUrl = System.getenv("git_api_url");
        String token = System.getenv("git_api_token");
        github = new GitHubBuilder().withEndpoint(gitApiUrl)
                .withOAuthToken(token)
                .build();
        github.checkApiUrlValidity();
        cleanBefore();

        GHOrganization org = github.getOrganization(ORG);

        GHRepository repo = org.createRepository(NAME)
                .description("Delete if this exists. If it exists, then an integration test crashed somewhere.")
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

        String login = github.getMyself().getLogin();
        GHRepository repo = github.getRepository(Paths.get(login, NAME).toString());

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
            validatePullRequestCreation(ORG, NAME, true);
        }
    }

    private void validatePullRequestCreation(String org, String repoName, boolean created) throws Exception {
        GHRepository parentRepo = github.getOrganization(org).getRepository(repoName);
        List<GHPullRequest> prs = parentRepo.getPullRequests(GHIssueState.OPEN);
        if (created) {
            Assert.assertEquals(prs.size(), 1);
        } else {
            Assert.assertEquals(prs.size(), 0);
        }
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
        checkAndDeleteBefore(repoNames);
    }

    @AfterClass
    public void cleanUp() throws Exception {
        List<Exception> exceptions = new ArrayList<>();

        exceptions.addAll(checkAndDelete(createdRepos));
        String login = github.getMyself().getLogin();
        GHRepository storeRepo = github.getRepository(Paths.get(login, STORE_NAME).toString());
        exceptions.add(checkAndDelete(storeRepo));

        for (int i = 0; i < exceptions.size(); i++) {
            log.error("Hit exception {}/{} while cleaning up.", exceptions.get(i), i, exceptions.size());
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

    private List<Exception> checkAndDeleteBefore(List<String> repoNames) throws IOException {
        List<Exception> exceptions = new ArrayList<>();

        GHRepository repo;
        for (String repoName : repoNames) {
            try {
                repo = github.getRepository(repoName);
            } catch (Exception e) {
                exceptions.add(e);
                continue;
            }
            try {
                repo.delete();
            } catch (Exception e) {
                exceptions.add(e);
            }
        }

        return exceptions;
    }
}
