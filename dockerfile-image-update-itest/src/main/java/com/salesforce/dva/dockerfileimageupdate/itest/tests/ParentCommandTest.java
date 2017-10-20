package com.salesforce.dva.dockerfileimageupdate.itest.tests;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
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

/**
 * Created by minho.park on 7/19/16.
 */
public class ParentCommandTest {
    private static final Logger log = LoggerFactory.getLogger(ParentCommandTest.class);

    private static final List<String> REPOS = Arrays.asList(
            "dockerfileImageUpdateParentITest1", "dockerfileImageUpdateParentITest2",
            "dockerfileImageUpdateParentITest3", "dockerfileImageUpdateParentITest4");

    private static final List<String> DUPLICATES = Arrays.asList(
            "dockerfileImageUpdateParentITest-Across1", "dockerfileImageUpdateParentITest-Across2");

    private static final List<String> DUPLICATES_CREATED_BY_GIT_HUB = Arrays.asList(
            DUPLICATES.get(0), DUPLICATES.get(0)+"-1", DUPLICATES.get(0)+"-2",
            DUPLICATES.get(1), DUPLICATES.get(1)+"-1", DUPLICATES.get(1)+"-2");

    private static final List<String> ORGS = Arrays.asList(
            "dockerfile-image-update-itest", "dockerfile-image-update-itest-2", "dockerfile-image-update-itest-3");

    private static final String IMAGE_1 = UUID.randomUUID().toString();
    private static final String IMAGE_2 = UUID.randomUUID().toString();
    private static final String TAG = UUID.randomUUID().toString();
    private static final String STORE_NAME = REPOS.get(0) + "-Store";


    private List<GHRepository> createdRepos = new ArrayList<>();
    private GithubUtil githubUtil; //initialized in setUp
    private GitHub github = null; //initialized in setUp


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
        githubUtil = new GithubUtil(github);

        cleanBefore();

        GHOrganization org = github.getOrganization(ORGS.get(0));
        TestCommon.initializeRepos(org, REPOS, IMAGE_1, createdRepos, githubUtil);

        for (String s: ORGS) {
            org = github.getOrganization(s);
            TestCommon.initializeRepos(org, DUPLICATES, IMAGE_2, createdRepos, githubUtil);
        }
        /* We need to wait because there is a delay on the search API used in the parent command; it takes time
         * for the search API to pick up recently created repositories.
         */
        checkIfSearchUpToDate("image1", IMAGE_1, REPOS.size());
        checkIfSearchUpToDate("image2", IMAGE_2, DUPLICATES.size() * ORGS.size());
    }

    private void checkIfSearchUpToDate(String imageName, String image, int numberOfRepos) throws InterruptedException {
        boolean bypassedDelay = false;
        for (int i = 0; i < 120; i++) {
            PagedSearchIterable<GHContent> searchImage1 = github.searchContent().
                    language("Dockerfile").q(image).list();
            log.info("Currently {} search gives {} results. It should be {}.", imageName,
                    searchImage1.getTotalCount(), numberOfRepos);
            if (searchImage1.getTotalCount() >= 4) {
                bypassedDelay = true;
                break;
            } else {
                Thread.sleep(3000);
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

    @Test
    public void testParent() throws Exception {
        ProcessBuilder builder = new ProcessBuilder("java", "-jar", "dockerfile-image-update.jar", "parent",
                IMAGE_1, TAG, STORE_NAME);
        builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        Process pc = builder.start(); // may throw IOException

        int exitcode = pc.waitFor();
        Assert.assertEquals(exitcode, 0, "Parent command for testParent failed.");

        for (String repoName : REPOS) {
            validateRepo(repoName, IMAGE_1);
        }
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
                    Assert.assertTrue(line.endsWith(TAG), "The tag has not been changed.");
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

    @Test(dependsOnMethods = "testParent")
    public void testIdempotency() throws Exception {
        testParent();
    }

    @Test
    public void testSameNameAcrossDifferentOrgs() throws Exception {
        ProcessBuilder builder = new ProcessBuilder("java", "-jar", "dockerfile-image-update.jar", "parent",
                IMAGE_2, TAG, STORE_NAME);
        builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        Process pc = builder.start(); // may throw IOException

        int exitcode = pc.waitFor();
        Assert.assertEquals(exitcode, 0, "Parent command for testSameNameAcrossDifferentOrgs failed.");

        for (String repoName : DUPLICATES_CREATED_BY_GIT_HUB) {
            validateRepo(repoName, IMAGE_2);
        }
    }

    @Test(dependsOnMethods = {"testIdempotency", "testSameNameAcrossDifferentOrgs"})
    public void testStoreUpdate() throws Exception {
        String user = github.getMyself().getLogin();
        GHRepository storeRepo = github.getRepository(Paths.get(user, STORE_NAME).toString());
        GHContent store = storeRepo.getFileContent("store.json");
        try (InputStream stream = store.read(); InputStreamReader streamR = new InputStreamReader(stream)) {
            JsonElement json = new JsonParser().parse(streamR);
            JsonElement images = json.getAsJsonObject().get("images");
            Assert.assertEquals(images.getAsJsonObject().get(IMAGE_1).getAsString(), TAG);
            Assert.assertEquals(images.getAsJsonObject().get(IMAGE_2).getAsString(), TAG);
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
}
