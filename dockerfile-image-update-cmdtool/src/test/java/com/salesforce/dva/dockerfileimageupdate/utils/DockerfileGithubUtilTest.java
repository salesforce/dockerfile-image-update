package com.salesforce.dva.dockerfileimageupdate.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.salesforce.dva.dockerfileimageupdate.githubutils.GithubUtil;
import org.kohsuke.github.*;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;

import static com.salesforce.dva.dockerfileimageupdate.utils.Constants.PULL_REQ_ID;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.times;
import static org.testng.Assert.*;

/**
 * Created by minho.park on 7/27/16.
 */
public class DockerfileGithubUtilTest {
    @Mock
    GithubUtil githubUtil;

    DockerfileGithubUtil dockerfileGithubUtil;

    @Test
    public void testGetGithubUtil() throws Exception {
        githubUtil = Mockito.mock(GithubUtil.class);
        dockerfileGithubUtil = new DockerfileGithubUtil(githubUtil);
        assertEquals(dockerfileGithubUtil.getGithubUtil(), githubUtil);
    }

    @Test
    public void testCheckFromParentAndFork() throws Exception {
        githubUtil = Mockito.mock(GithubUtil.class);

        GHRepository parent = Mockito.mock(GHRepository.class);
        GHRepository fork = Mockito.mock(GHRepository.class);
        Mockito.when(fork.getOwnerName()).thenReturn("me");

        Mockito.doCallRealMethod().when(githubUtil).safeDeleteRepo(fork);

        GHMyself myself = Mockito.mock(GHMyself.class);
        Mockito.when(myself.getLogin()).thenReturn("me");

        PagedIterable<GHRepository> listOfForks = Mockito.mock(PagedIterable.class);
        PagedIterator<GHRepository> listOfForksIterator = Mockito.mock(PagedIterator.class);

        Mockito.when(githubUtil.createFork(parent)).thenReturn(new GHRepository());
        Mockito.when(githubUtil.getMyself()).thenReturn(myself);

        Mockito.when(listOfForksIterator.next()).thenReturn(fork);
        Mockito.when(listOfForksIterator.hasNext()).thenReturn(true);
        Mockito.when(listOfForks.iterator()).thenReturn(listOfForksIterator);
        Mockito.when(parent.listForks()).thenReturn(listOfForks);

        dockerfileGithubUtil = new DockerfileGithubUtil(githubUtil);
        mockPullRequestAlreadyExists_True(parent, myself);
        GHRepository returnRepo = dockerfileGithubUtil.checkFromParentAndFork(parent);
        assertNotNull(returnRepo);
        Mockito.verify(githubUtil, times(1)).createFork(parent);

        mockPullRequestAlreadyExists_False(parent, myself);
        returnRepo = dockerfileGithubUtil.checkFromParentAndFork(parent);
        assertNotNull(returnRepo);
        Mockito.verify(githubUtil, times(1+2)).createFork(parent);
        Mockito.verify(fork, times(1)).delete();

        mockPullRequestAlreadyExists_Error(parent);
        returnRepo = dockerfileGithubUtil.checkFromParentAndFork(parent);
        assertNotNull(returnRepo);
        Mockito.verify(githubUtil, times(1+2+2)).createFork(parent);
        Mockito.verify(fork, times(2)).delete();
    }

    @Test
    public void testCheckFromParentAndFork_returnNull() throws Exception {
        githubUtil = Mockito.mock(GithubUtil.class);

        GHRepository parent = Mockito.mock(GHRepository.class);
        GHRepository fork = Mockito.mock(GHRepository.class);
        Mockito.when(fork.getOwnerName()).thenReturn("you");

        GHMyself myself = Mockito.mock(GHMyself.class);
        Mockito.when(myself.getLogin()).thenReturn("me");

        PagedIterable<GHRepository> listOfForks = Mockito.mock(PagedIterable.class);
        PagedIterator<GHRepository> listOfForksIterator = Mockito.mock(PagedIterator.class);

        Mockito.when(githubUtil.createFork(parent)).thenReturn(new GHRepository());
        Mockito.when(githubUtil.getMyself()).thenReturn(myself);

        Mockito.when(listOfForksIterator.next()).thenReturn(fork);
        Mockito.when(listOfForksIterator.hasNext()).thenReturn(true, false);
        Mockito.when(listOfForks.iterator()).thenReturn(listOfForksIterator);
        Mockito.when(parent.listForks()).thenReturn(listOfForks);

        dockerfileGithubUtil = new DockerfileGithubUtil(githubUtil);
        GHRepository returnRepo = dockerfileGithubUtil.checkFromParentAndFork(parent);
        assertNull(returnRepo);
    }

    private void mockPullRequestAlreadyExists_True(GHRepository parent, GHMyself myself) throws IOException {
        List<GHPullRequest> pullRequests = Mockito.mock(List.class);
        Iterator<GHPullRequest> pullRequestIterator = Mockito.mock(Iterator.class);


        GHPullRequest ghPullRequest = Mockito.mock(GHPullRequest.class);
        Mockito.when(ghPullRequest.getBody()).thenReturn(PULL_REQ_ID);
        GHCommitPointer head = Mockito.mock(GHCommitPointer.class);

        Mockito.when(head.getUser()).thenReturn(myself);
        Mockito.when(ghPullRequest.getHead()).thenReturn(head);

        Mockito.when(pullRequestIterator.next()).thenReturn(ghPullRequest);
        Mockito.when(pullRequestIterator.hasNext()).thenReturn(true);
        Mockito.when(pullRequests.iterator()).thenReturn(pullRequestIterator);

        Mockito.when(parent.getPullRequests(GHIssueState.OPEN)).thenReturn(pullRequests);
    }

    private void mockPullRequestAlreadyExists_False(GHRepository parent, GHMyself myself) throws IOException {
        List<GHPullRequest> pullRequests = Mockito.mock(List.class);
        Iterator<GHPullRequest> pullRequestIterator = Mockito.mock(Iterator.class);


        GHPullRequest ghPullRequest = Mockito.mock(GHPullRequest.class);
        Mockito.when(ghPullRequest.getBody()).thenReturn("-1");
        GHCommitPointer head = Mockito.mock(GHCommitPointer.class);

        Mockito.when(head.getUser()).thenReturn(myself);
        Mockito.when(ghPullRequest.getHead()).thenReturn(head);

        Mockito.when(pullRequestIterator.next()).thenReturn(ghPullRequest);
        Mockito.when(pullRequestIterator.hasNext()).thenReturn(true, false);
        Mockito.when(pullRequests.iterator()).thenReturn(pullRequestIterator);

        Mockito.when(parent.getPullRequests(GHIssueState.OPEN)).thenReturn(pullRequests);
    }

    private void mockPullRequestAlreadyExists_Error(GHRepository parent) throws Exception {
        Mockito.when(parent.getPullRequests(GHIssueState.OPEN)).thenThrow(new IOException());
    }

    @Test
    public void testGetMyself() throws Exception {
        githubUtil = Mockito.mock(GithubUtil.class);

        GHMyself myself = Mockito.mock(GHMyself.class);
        Mockito.when(githubUtil.getMyself()).thenReturn(myself);
        dockerfileGithubUtil = new DockerfileGithubUtil(githubUtil);
        assertEquals(dockerfileGithubUtil.getMyself(), myself);
    }

    @Test
    public void testGetRepo() throws Exception {
        githubUtil = Mockito.mock(GithubUtil.class);

        GHRepository repo = Mockito.mock(GHRepository.class);
        Mockito.when(githubUtil.getRepo(eq("test"))).thenReturn(repo);
        dockerfileGithubUtil = new DockerfileGithubUtil(githubUtil);
        assertEquals(dockerfileGithubUtil.getRepo("test"), repo);
    }



    @DataProvider
    public Object[][] inputEmptyImages() {
        return new Object[][] {
                {" ", null},
                {"  ", "test"},
                {"      ", null},
                {"          ", "test"},
                {"                  ", null},
                {"\n", null},
                {"      \n           \n", "test"},
                {"\t", null},
                {" \t        \t", null},
                {"\r", "test"},
                {" \r      \r", null},
                {" \n  \t    \r ", null},
        };
    }

    @Test(dataProvider = "inputEmptyImages", expectedExceptions = IOException.class)
    public void testFindFiles_EmptyQuery(String query, String org) throws Exception {
        githubUtil = Mockito.mock(GithubUtil.class);

        GHContentSearchBuilder ghContentSearchBuilder = Mockito.mock(GHContentSearchBuilder.class);
        PagedSearchIterable<GHContent> list = Mockito.mock(PagedSearchIterable.class);
        Mockito.when(list.getTotalCount()).thenReturn(0);

        Mockito.when(ghContentSearchBuilder.list()).thenReturn(list);
        Mockito.when(githubUtil.startSearch()).thenReturn(ghContentSearchBuilder);

        dockerfileGithubUtil = new DockerfileGithubUtil(githubUtil);
        dockerfileGithubUtil.findFilesWithImage(query, org);
    }

    @Test
    public void testFindFiles() throws Exception {
        githubUtil = Mockito.mock(GithubUtil.class);

        GHContentSearchBuilder ghContentSearchBuilder = Mockito.mock(GHContentSearchBuilder.class);
        PagedSearchIterable<GHContent> list = Mockito.mock(PagedSearchIterable.class);
        Mockito.when(list.getTotalCount()).thenReturn(100);

        Mockito.when(ghContentSearchBuilder.list()).thenReturn(list);
        Mockito.when(githubUtil.startSearch()).thenReturn(ghContentSearchBuilder);

        dockerfileGithubUtil = new DockerfileGithubUtil(githubUtil);
        assertEquals(dockerfileGithubUtil.findFilesWithImage("test", "test"), list);
    }

    @Test(dependsOnMethods = "testFindImagesAndFix")
    public void testModifyOnGithubRecursive() throws Exception {
        githubUtil = Mockito.mock(GithubUtil.class);

        GHRepository repo = Mockito.mock(GHRepository.class);
        List<GHContent> tree = Mockito.mock(List.class);
        Iterator<GHContent> treeIterator = Mockito.mock(Iterator.class);
        GHContent content = Mockito.mock(GHContent.class);
        Mockito.when(content.getType()).thenReturn("tree", "file");
        Mockito.when(content.getPath()).thenReturn("path");
        Mockito.when(content.read()).thenReturn(new InputStream() {
            @Override
            public int read() throws IOException {
                return -1;
            }
        });
        Mockito.when(treeIterator.hasNext()).thenReturn(true, true, true, true, true, false);
        Mockito.when(treeIterator.next()).thenReturn(content);
        Mockito.when(tree.iterator()).thenReturn(treeIterator);
        String branch = "branch";
        String img = "img";
        String tag = "tag";

        Mockito.when(repo.getDirectoryContent("path", branch)).thenReturn(tree);

        dockerfileGithubUtil = new DockerfileGithubUtil(githubUtil);
        dockerfileGithubUtil.modifyOnGithubRecursive(repo, content, branch, img, tag);

        Mockito.verify(content, times(6)).getType();

    }

    @Test
    public void testTryRetrievingContent() throws Exception {
        githubUtil = Mockito.mock(GithubUtil.class);

        GHContent content = Mockito.mock(GHContent.class);
        Mockito.when(githubUtil.tryRetrievingContent(any(), anyString(), anyString())).thenReturn(content);
        dockerfileGithubUtil = new DockerfileGithubUtil(githubUtil);
        assertEquals(dockerfileGithubUtil.tryRetrievingContent(new GHRepository(), "path", "branch"), content);
    }


    @DataProvider
    public Object[][] inputBranchesImagesAndTags() {
        return new Object[][] {
                {"master", "image1", "image1", "6", "7", 1},
                {"branch", "image1", "image2", "7", "7", 0},
                {"master", "image1", "image1", "6", "7", 1},
                {"branch2", "image1", "image2", "6", "7", 0},
                {"master", "image1", "image1", "6", "7", 1},
                {"branch", "image1", "image1", "7", "7", 0},
                {"master", "image1", "image2", "6", "7", 0},
                {"branch3", "image1", "image1", "6", "7", 1},
                {"master", "image1", "image1", "6", "7", 1},
                {"branch", "image1", "image1", "7", "7", 0},
                {"master", "image1", "image1", "6", "7", 1},
                {"branch5", "image1", "image1", "6", "7", 1},
        };
    }

    @Test(dataProvider = "inputBranchesImagesAndTags")
    public void testFindImagesAndFix(String branch, String currentImage,
                                     String searchImage, String currentTag,
                                     String newTag, int modified) throws Exception {
        githubUtil = Mockito.mock(GithubUtil.class);

        BufferedReader reader = Mockito.mock(BufferedReader.class);
        GHContent content = Mockito.mock(GHContent.class);
        Mockito.when(content.getPath()).thenReturn("path");
        Mockito.when(content.update(anyString(), anyString(), eq(branch))).thenReturn(null);

        Mockito.when(reader.readLine()).thenReturn("FROM " + currentImage + ":" + currentTag, "", null);
        dockerfileGithubUtil = new DockerfileGithubUtil(githubUtil);
        dockerfileGithubUtil.findImagesAndFix(content, branch, searchImage, newTag, "", reader);
        Mockito.verify(content, times(modified)).update(anyString(), anyString(), eq(branch));
    }

    @Test(dataProvider = "inputBranchesImagesAndTags")
    public void testFindImagesAndFix_KeepLinesIntact(String branch, String currentImage,
                                                     String searchImage, String currentTag,
                                                     String newTag, int modified) throws Exception {
        githubUtil = Mockito.mock(GithubUtil.class);

        BufferedReader reader = Mockito.mock(BufferedReader.class);
        GHContent content = Mockito.mock(GHContent.class);
        Mockito.when(content.getPath()).thenReturn("path");
        Mockito.when(content.update(anyString(), anyString(), eq(branch))).thenReturn(null);

        Mockito.when(reader.readLine()).thenReturn("blahblahblah", "FROM " + currentImage + ":" + currentTag,
                "blahblahblah", null);
        dockerfileGithubUtil = new DockerfileGithubUtil(githubUtil);
        dockerfileGithubUtil.findImagesAndFix(content, branch, searchImage, newTag, "", reader);
        Mockito.verify(content, times(modified)).update(anyString(), anyString(), eq(branch));
    }

    @Test
    public void testFindImagesAndFix_doNotDeleteOtherLines() throws Exception {
        githubUtil = Mockito.mock(GithubUtil.class);

        BufferedReader reader = Mockito.mock(BufferedReader.class);
        GHContent content = Mockito.mock(GHContent.class);
        Mockito.when(content.getPath()).thenReturn("path");
        Mockito.when(content.update(anyString(), anyString(), anyString())).thenReturn(null);

        Mockito.when(reader.readLine()).thenReturn("hello", "FROM image:tag",
                "this is a test", "", "", "", "world", null);

        dockerfileGithubUtil = new DockerfileGithubUtil(githubUtil);

        StringBuilder strB = new StringBuilder();
        dockerfileGithubUtil.rewriteDockerfile("image", "newtag", reader, strB);

        assertEquals(strB.toString(), "hello\nFROM image:newtag\nthis is a test\n\n\n\nworld\n");
    }

    @DataProvider
    public Object[][] inputlines() throws Exception {
        return new Object[][]{
                {"image1", "3", "FROM image1_blahblah", false},
                {"image1", "3", "  FROM image1_blahblah", false},
                {"image1", "3", "FROM image1_blahblah  ", false},
                {"image1", "3", "FROM image1_blahblah\t", false},
                {"image1", "3", "FROM image1_blahblah:2", false},
                {"image2", "4", "FROM image2_blahblah:latest", false},
                {"image4", "9", "FROM image4:9", false},
                {"image5", "246", "FROM image5_", false},
                {"image6", "26", "FROM image7", false},
                {"image8", "245", "FROM image8:245", false},
                {"image8", "245", "FROM image8: 245", true},
                {"image3", "7", "FROM image3", true},
                {"image3", "7", "  FROM image3", true},
                {"image3", "7", "FROM image3  ", true},
                {"image3", "7", "\tFROM image3\t", true},
                {"image7", "98", "FROM image7:4", true},
                {"image7", "98", "FROM image7: 4", true},
                {"image124516418023_1085-1-1248571", "7357",
                        "FROM image124516418023_1085-1-1248571:18026809126359806124890356219518632048125", true}
        };
    }

    @Test(dataProvider = "inputlines")
    public void testChangeIfDockerfileBaseImageLine(String img, String tag,
                                                    String line, boolean expected) throws Exception {
        githubUtil = Mockito.mock(GithubUtil.class);
        dockerfileGithubUtil = new DockerfileGithubUtil(githubUtil);
        assertEquals(dockerfileGithubUtil.changeIfDockerfileBaseImageLine(img, tag, new StringBuilder(), line),
                expected);
    }

    @Test
    public void testChangeIfDockerfileBaseImageLine_modifyingStringBuilder() throws Exception {
        githubUtil = Mockito.mock(GithubUtil.class);
        dockerfileGithubUtil = new DockerfileGithubUtil(githubUtil);
        StringBuilder stringBuilder = new StringBuilder();
        String img = "image";
        String tag = "7357";
        dockerfileGithubUtil.changeIfDockerfileBaseImageLine(img, tag, stringBuilder, "hello");
        dockerfileGithubUtil.changeIfDockerfileBaseImageLine(img, tag, stringBuilder, "FROM image:blah");
        dockerfileGithubUtil.changeIfDockerfileBaseImageLine(img, tag, stringBuilder, "world");
        dockerfileGithubUtil.changeIfDockerfileBaseImageLine(img, tag, stringBuilder, "this is a test");
        assertEquals(stringBuilder.toString(), "hello\nFROM image:7357\nworld\nthis is a test\n");
    }

    @DataProvider
    public Object[][] inputStores() throws Exception {
        return new Object[][] {
                {"{\n  \"images\": {\n" +
                        "    \"test\": \"testingtest\",\n" +
                        "    \"asdfjkl\": \"asdfjkl\",\n" +
                        "    \"7fce8488-31f4-4137-ad68-c19c3b33eebb\": \"manualtest\"\n" +
                        "  }\n" +
                        "}",
                        "test", "newtag",
                        "{\n  \"images\": {\n" +
                                "    \"test\": \"newtag\",\n" +
                                "    \"asdfjkl\": \"asdfjkl\",\n" +
                                "    \"7fce8488-31f4-4137-ad68-c19c3b33eebb\": \"manualtest\"\n" +
                                "  }\n" +
                                "}"},
                {"{\n  \"images\": {\n" +
                        "    \"test\": \"testingtest\",\n" +
                        "    \"asdfjkl\": \"asdfjkl\",\n" +
                        "    \"7fce8488-31f4-4137-ad68-c19c3b33eebb\": \"manualtest\"\n" +
                        "  }\n" +
                        "}",
                        "test", "test",
                        "{\n  \"images\": {\n" +
                                "    \"test\": \"test\",\n" +
                                "    \"asdfjkl\": \"asdfjkl\",\n" +
                                "    \"7fce8488-31f4-4137-ad68-c19c3b33eebb\": \"manualtest\"\n" +
                                "  }\n" +
                                "}"},
                {"{\n  \"images\": {\n" +
                        "    \"test\": \"testingtest\",\n" +
                        "    \"asdfjkl\": \"asdfjkl\",\n" +
                        "    \"7fce8488-31f4-4137-ad68-c19c3b33eebb\": \"manualtest\"\n" +
                        "  }\n" +
                        "}",
                        "newImage", "newtag2",
                        "{\n  \"images\": {\n" +
                                "    \"test\": \"testingtest\",\n" +
                                "    \"asdfjkl\": \"asdfjkl\",\n" +
                                "    \"7fce8488-31f4-4137-ad68-c19c3b33eebb\": \"manualtest\",\n" +
                                "    \"newImage\": \"newtag2\"\n" +
                                "  }\n" +
                                "}"},
                {"{}", "image", "tag",
                        "{\n  \"images\": {\n" +
                                "    \"image\": \"tag\"\n" +
                                "  }\n" +
                                "}"},
                {"{}", "test", "test",
                        "{\n  \"images\": {\n" +
                                "    \"test\": \"test\"\n" +
                                "  }\n" +
                                "}"},
                {"", "image", "tag",
                        "{\n  \"images\": {\n" +
                                "    \"image\": \"tag\"\n" +
                                "  }\n" +
                                "}"},
                {"{\n  \"images\": {\n" +
                        "  }\n" +
                        "}",
                        "image", "tag",
                        "{\n  \"images\": {\n" +
                                "    \"image\": \"tag\"\n" +
                                "  }\n" +
                                "}"}

        };
    }

    @Test(dataProvider = "inputStores")
    public void testGetAndModifyJsonString(String storeContent, String image, String tag, String expectedOutput) throws Exception {
        githubUtil = Mockito.mock(GithubUtil.class);

        JsonElement json = new JsonParser().parse(storeContent);

        dockerfileGithubUtil = new DockerfileGithubUtil(githubUtil);
        String output = dockerfileGithubUtil.getAndModifyJsonString(json, image, tag);
        assertEquals(output, expectedOutput);
    }

    @Test
    public void testCreatePullReq_Loop() throws Exception {
        githubUtil = Mockito.mock(GithubUtil.class);

        Mockito.when(githubUtil.createPullReq(any(), anyString(), any(), anyString(), eq(PULL_REQ_ID))).thenReturn(-1, -1, -1, 0);
        dockerfileGithubUtil = new DockerfileGithubUtil(githubUtil);
        dockerfileGithubUtil.createPullReq(new GHRepository(), "branch", new GHRepository(), "Automatic Dockerfile Image Updater");
        Mockito.verify(githubUtil, times(4)).createPullReq(any(), anyString(), any(), anyString(), eq(PULL_REQ_ID));
    }

    @Test
    public void testCreatePullReq_Delete() throws Exception {
        githubUtil = Mockito.mock(GithubUtil.class);

        GHRepository forkRepo = Mockito.mock(GHRepository.class);
        Mockito.when(githubUtil.createPullReq(any(), anyString(), any(), anyString(), eq(PULL_REQ_ID))).thenReturn(1);
        Mockito.doCallRealMethod().when(githubUtil).safeDeleteRepo(forkRepo);
        dockerfileGithubUtil = new DockerfileGithubUtil(githubUtil);
        dockerfileGithubUtil.createPullReq(new GHRepository(), "branch", forkRepo, "Automatic Dockerfile Image Updater");
        Mockito.verify(githubUtil, times(1)).createPullReq(any(), anyString(), any(), anyString(), eq(PULL_REQ_ID));
        Mockito.verify(forkRepo, times(1)).delete();
    }

}