/*
 * Copyright (c) 2017, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.kohsuke.github.*;
import org.mockito.Mock;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

/**
 * Created by minho.park on 7/27/16.
 */
public class DockerfileGitHubUtilTest {
    @Mock
    GitHubUtil gitHubUtil;

    DockerfileGitHubUtil dockerfileGitHubUtil;

    @Test
    public void testGetGithubUtil() throws Exception {
        gitHubUtil = mock(GitHubUtil.class);
        dockerfileGitHubUtil = new DockerfileGitHubUtil(gitHubUtil);
        assertEquals(dockerfileGitHubUtil.getGitHubUtil(), gitHubUtil);
    }

    @Test
    public void testExistingPullRequestClosedAndParentIsForkedOnlyOnce() throws Exception {
        gitHubUtil = mock(GitHubUtil.class);

        GHRepository parent = mock(GHRepository.class);
        GHRepository fork = mock(GHRepository.class);
        when(fork.getOwnerName()).thenReturn("me");

        doCallRealMethod().when(gitHubUtil).safeDeleteRepo(fork);

        GHMyself myself = mock(GHMyself.class);
        when(myself.getLogin()).thenReturn("me");

        PagedIterable<GHRepository> listOfForks = mock(PagedIterable.class);
        PagedIterator<GHRepository> listOfForksIterator = mock(PagedIterator.class);

        when(gitHubUtil.createFork(parent)).thenReturn(new GHRepository());
        when(gitHubUtil.getMyself()).thenReturn(myself);

        when(listOfForksIterator.next()).thenReturn(fork);
        when(listOfForksIterator.hasNext()).thenReturn(true, false);
        when(listOfForks.iterator()).thenReturn(listOfForksIterator);
        when(parent.listForks()).thenReturn(listOfForks);

        dockerfileGitHubUtil = new DockerfileGitHubUtil(gitHubUtil);
        GHPullRequest ghPullRequest = mockPullRequestAlreadyExists_True(parent, myself);
        GHRepository returnRepo = dockerfileGitHubUtil.closeOutdatedPullRequestAndFork(parent);
        verify(ghPullRequest, times(1)).close();
        verify(gitHubUtil, times(1)).createFork(parent);
        assertNotNull(returnRepo);
    }

    @Test
    public void testParentIsForked_whenNoPullRequestExists() throws Exception {
        gitHubUtil = mock(GitHubUtil.class);

        GHRepository parent = mock(GHRepository.class);
        GHRepository fork = mock(GHRepository.class);
        when(fork.getOwnerName()).thenReturn("me");

        doCallRealMethod().when(gitHubUtil).safeDeleteRepo(fork);

        GHMyself myself = mock(GHMyself.class);
        when(myself.getLogin()).thenReturn("me");

        PagedIterable<GHRepository> listOfForks = mock(PagedIterable.class);
        PagedIterator<GHRepository> listOfForksIterator = mock(PagedIterator.class);

        when(gitHubUtil.createFork(parent)).thenReturn(new GHRepository());
        when(gitHubUtil.getMyself()).thenReturn(myself);

        when(listOfForksIterator.next()).thenReturn(fork);
        when(listOfForksIterator.hasNext()).thenReturn(true, false);
        when(listOfForks.iterator()).thenReturn(listOfForksIterator);
        when(parent.listForks()).thenReturn(listOfForks);

        dockerfileGitHubUtil = new DockerfileGitHubUtil(gitHubUtil);
        GHPullRequest ghPullRequest = mockPullRequestAlreadyExists_False(parent, myself);
        GHRepository returnRepo = dockerfileGitHubUtil.closeOutdatedPullRequestAndFork(parent);
        // No PR exists hence PullRequest.close() is not called
        verify(ghPullRequest, times(0)).close();
        verify(gitHubUtil, times(1)).createFork(parent);
        verify(fork, times(1)).delete();
        assertNotNull(returnRepo);
    }

    @Test
    public void testParentIsForked_whenPullRequestFetchThrowsIOException() throws
            Exception {
        gitHubUtil = mock(GitHubUtil.class);

        GHRepository parent = mock(GHRepository.class);
        GHRepository fork = mock(GHRepository.class);
        when(fork.getOwnerName()).thenReturn("me");

        doCallRealMethod().when(gitHubUtil).safeDeleteRepo(fork);

        GHMyself myself = mock(GHMyself.class);
        when(myself.getLogin()).thenReturn("me");

        PagedIterable<GHRepository> listOfForks = mock(PagedIterable.class);
        PagedIterator<GHRepository> listOfForksIterator = mock(PagedIterator.class);

        when(gitHubUtil.createFork(parent)).thenReturn(new GHRepository());
        when(gitHubUtil.getMyself()).thenReturn(myself);

        when(listOfForksIterator.next()).thenReturn(fork);
        when(listOfForksIterator.hasNext()).thenReturn(true, false);
        when(listOfForks.iterator()).thenReturn(listOfForksIterator);
        when(parent.listForks()).thenReturn(listOfForks);

        dockerfileGitHubUtil = new DockerfileGitHubUtil(gitHubUtil);
        mockPullRequestAlreadyExists_Error(parent);
        GHRepository returnRepo = dockerfileGitHubUtil.closeOutdatedPullRequestAndFork(parent);
        verify(gitHubUtil, times(1)).createFork(parent);
        verify(fork, times(1)).delete();
        assertNotNull(returnRepo);
    }

    @Test
    public void testForkedRepoIsNullWhenForkCreationThrowsIOException() throws Exception {
        gitHubUtil = mock(GitHubUtil.class);

        GHRepository parent = mock(GHRepository.class);
        GHRepository fork = mock(GHRepository.class);
        when(fork.getOwnerName()).thenReturn("you");

        GHMyself myself = mock(GHMyself.class);
        when(myself.getLogin()).thenReturn("me");

        PagedIterable<GHRepository> listOfForks = mock(PagedIterable.class);
        PagedIterator<GHRepository> listOfForksIterator = mock(PagedIterator.class);

        // fork creation throws IOException
        when(parent.fork()).thenThrow(IOException.class);

        when(gitHubUtil.getMyself()).thenReturn(myself);

        when(listOfForksIterator.next()).thenReturn(fork);
        when(listOfForksIterator.hasNext()).thenReturn(true, false);
        when(listOfForks.iterator()).thenReturn(listOfForksIterator);
        when(parent.listForks()).thenReturn(listOfForks);

        dockerfileGitHubUtil = new DockerfileGitHubUtil(gitHubUtil);
        GHRepository returnRepo = dockerfileGitHubUtil.closeOutdatedPullRequestAndFork(parent);
        assertNull(returnRepo);
    }

    private GHPullRequest mockPullRequestAlreadyExists_True(GHRepository parent, GHMyself myself) throws IOException {
        List<GHPullRequest> pullRequests = mock(List.class);
        Iterator<GHPullRequest> pullRequestIterator = mock(Iterator.class);


        GHPullRequest ghPullRequest = mock(GHPullRequest.class);
        when(ghPullRequest.getBody()).thenReturn(Constants.PULL_REQ_ID);
        GHCommitPointer head = mock(GHCommitPointer.class);

        when(head.getUser()).thenReturn(myself);
        when(ghPullRequest.getHead()).thenReturn(head);

        when(pullRequestIterator.next()).thenReturn(ghPullRequest);
        when(pullRequestIterator.hasNext()).thenReturn(true);
        when(pullRequests.iterator()).thenReturn(pullRequestIterator);

        when(parent.getPullRequests(GHIssueState.OPEN)).thenReturn(pullRequests);
        return ghPullRequest;
    }

    private GHPullRequest mockPullRequestAlreadyExists_False(GHRepository parent, GHMyself myself) throws IOException {
        List<GHPullRequest> pullRequests = mock(List.class);
        Iterator<GHPullRequest> pullRequestIterator = mock(Iterator.class);


        GHPullRequest ghPullRequest = mock(GHPullRequest.class);
        when(ghPullRequest.getBody()).thenReturn("-1");
        GHCommitPointer head = mock(GHCommitPointer.class);

        when(head.getUser()).thenReturn(myself);
        when(ghPullRequest.getHead()).thenReturn(head);

        when(pullRequestIterator.next()).thenReturn(ghPullRequest);
        when(pullRequestIterator.hasNext()).thenReturn(true, false);
        when(pullRequests.iterator()).thenReturn(pullRequestIterator);

        when(parent.getPullRequests(GHIssueState.OPEN)).thenReturn(pullRequests);
        return ghPullRequest;
    }

    private void mockPullRequestAlreadyExists_Error(GHRepository parent) throws Exception {
        when(parent.getPullRequests(GHIssueState.OPEN)).thenThrow(new IOException());
    }

    @Test
    public void testGetMyself() throws Exception {
        gitHubUtil = mock(GitHubUtil.class);

        GHMyself myself = mock(GHMyself.class);
        when(gitHubUtil.getMyself()).thenReturn(myself);
        dockerfileGitHubUtil = new DockerfileGitHubUtil(gitHubUtil);
        assertEquals(dockerfileGitHubUtil.getMyself(), myself);
    }

    @Test
    public void testGetRepo() throws Exception {
        gitHubUtil = mock(GitHubUtil.class);

        GHRepository repo = mock(GHRepository.class);
        when(gitHubUtil.getRepo(eq("test"))).thenReturn(repo);
        dockerfileGitHubUtil = new DockerfileGitHubUtil(gitHubUtil);
        assertEquals(dockerfileGitHubUtil.getRepo("test"), repo);
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
        gitHubUtil = mock(GitHubUtil.class);

        GHContentSearchBuilder ghContentSearchBuilder = mock(GHContentSearchBuilder.class);
        PagedSearchIterable<GHContent> list = mock(PagedSearchIterable.class);
        when(list.getTotalCount()).thenReturn(0);

        when(ghContentSearchBuilder.list()).thenReturn(list);
        when(gitHubUtil.startSearch()).thenReturn(ghContentSearchBuilder);

        dockerfileGitHubUtil = new DockerfileGitHubUtil(gitHubUtil);
        dockerfileGitHubUtil.findFilesWithImage(query, org);
    }

    @Test
    public void testFindFiles() throws Exception {
        gitHubUtil = mock(GitHubUtil.class);

        GHContentSearchBuilder ghContentSearchBuilder = mock(GHContentSearchBuilder.class);
        PagedSearchIterable<GHContent> list = mock(PagedSearchIterable.class);
        when(list.getTotalCount()).thenReturn(100);

        when(ghContentSearchBuilder.list()).thenReturn(list);
        when(gitHubUtil.startSearch()).thenReturn(ghContentSearchBuilder);

        dockerfileGitHubUtil = new DockerfileGitHubUtil(gitHubUtil);
        assertEquals(dockerfileGitHubUtil.findFilesWithImage("test", "test"), list);
    }

    @Test(dependsOnMethods = "testFindImagesAndFix")
    public void testModifyOnGithubRecursive() throws Exception {
        gitHubUtil = mock(GitHubUtil.class);

        GHRepository repo = mock(GHRepository.class);
        List<GHContent> tree = mock(List.class);
        Iterator<GHContent> treeIterator = mock(Iterator.class);
        GHContent content = mock(GHContent.class);
        when(content.isFile()).thenReturn(false, true);
        when(content.getDownloadUrl()).thenReturn(null,  " ");
        when(content.isDirectory()).thenReturn(true, false);
        when(content.getPath()).thenReturn("path");
        when(content.read()).thenReturn(new InputStream() {
            @Override
            public int read() throws IOException {
                return -1;
            }
        });
        when(treeIterator.hasNext()).thenReturn(true, true, true, true, true, false);
        when(treeIterator.next()).thenReturn(content);
        when(tree.iterator()).thenReturn(treeIterator);
        String branch = "branch";
        String img = "img";
        String tag = "tag";

        when(repo.getDirectoryContent("path", branch)).thenReturn(tree);

        dockerfileGitHubUtil = new DockerfileGitHubUtil(gitHubUtil);
        dockerfileGitHubUtil.modifyOnGithubRecursive(repo, content, branch, img, tag);

        verify(content, times(6)).isFile();
        verify(content, times(2)).isDirectory();
        verify(content, times(5)).getDownloadUrl();
    }

    @Test
    public void testTryRetrievingContent() throws Exception {
        gitHubUtil = mock(GitHubUtil.class);

        GHContent content = mock(GHContent.class);
        when(gitHubUtil.tryRetrievingContent(any(), anyString(), anyString())).thenReturn(content);
        dockerfileGitHubUtil = new DockerfileGitHubUtil(gitHubUtil);
        assertEquals(dockerfileGitHubUtil.tryRetrievingContent(new GHRepository(), "path", "branch"), content);
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
        gitHubUtil = mock(GitHubUtil.class);

        BufferedReader reader = mock(BufferedReader.class);
        GHContent content = mock(GHContent.class);
        when(content.getPath()).thenReturn("path");
        when(content.update(anyString(), anyString(), eq(branch))).thenReturn(null);

        when(reader.readLine()).thenReturn("FROM " + currentImage + ":" + currentTag, "", null);
        dockerfileGitHubUtil = new DockerfileGitHubUtil(gitHubUtil);
        dockerfileGitHubUtil.findImagesAndFix(content, branch, searchImage, newTag, "", reader);
        verify(content, times(modified)).update(anyString(), anyString(), eq(branch));
    }

    @Test(dataProvider = "inputBranchesImagesAndTags")
    public void testFindImagesAndFix_KeepLinesIntact(String branch, String currentImage,
                                                     String searchImage, String currentTag,
                                                     String newTag, int modified) throws Exception {
        gitHubUtil = mock(GitHubUtil.class);

        BufferedReader reader = mock(BufferedReader.class);
        GHContent content = mock(GHContent.class);
        when(content.getPath()).thenReturn("path");
        when(content.update(anyString(), anyString(), eq(branch))).thenReturn(null);

        when(reader.readLine()).thenReturn("blahblahblah", "FROM " + currentImage + ":" + currentTag,
                "blahblahblah", null);
        dockerfileGitHubUtil = new DockerfileGitHubUtil(gitHubUtil);
        dockerfileGitHubUtil.findImagesAndFix(content, branch, searchImage, newTag, "", reader);
        verify(content, times(modified)).update(anyString(), anyString(), eq(branch));
    }

    @Test
    public void testFindImagesAndFix_doNotDeleteOtherLines() throws Exception {
        gitHubUtil = mock(GitHubUtil.class);

        BufferedReader reader = mock(BufferedReader.class);
        GHContent content = mock(GHContent.class);
        when(content.getPath()).thenReturn("path");
        when(content.update(anyString(), anyString(), anyString())).thenReturn(null);

        when(reader.readLine()).thenReturn("hello", "FROM image:tag",
                "this is a test", "", "", "", "world", null);

        dockerfileGitHubUtil = new DockerfileGitHubUtil(gitHubUtil);

        StringBuilder strB = new StringBuilder();
        dockerfileGitHubUtil.rewriteDockerfile("image", "newtag", reader, strB);

        assertEquals(strB.toString(), "hello\nFROM image:newtag\nthis is a test\n\n\n\nworld\n");
    }

    @DataProvider
    public Object[][] postTagData() {
        return new Object[][] {
                { ":tag as builder", "newtag", "FROM image:newtag as builder"},
                { ":tag#as builder", "newtag", "FROM image:newtag #as builder"},
                { ":tag # comment", "newtag", "FROM image:newtag # comment"},
                { ":tag\t# comment", "newtag", "FROM image:newtag # comment"},
                { ":", "newtag", "FROM image:newtag"},
                { ":test # :comment", "newtag", "FROM image:newtag # :comment"}
        };
    }

    @Test(dataProvider = "postTagData")
    public void testFindImagesAndFix_doNotDeletePostTagData(String postTagData, String updatedTag,
                                                            String expectedReplacedData) throws Exception {
        gitHubUtil = mock(GitHubUtil.class);

        BufferedReader reader = mock(BufferedReader.class);
        GHContent content = mock(GHContent.class);
        when(content.getPath()).thenReturn("path");
        when(content.update(anyString(), anyString(), anyString())).thenReturn(null);

        when(reader.readLine()).thenReturn("hello", "FROM image" + postTagData,
                "this is a test", null);

        dockerfileGitHubUtil = new DockerfileGitHubUtil(gitHubUtil);

        StringBuilder strB = new StringBuilder();
        boolean modified = dockerfileGitHubUtil.rewriteDockerfile("image", updatedTag, reader, strB);

        assertTrue(modified, "Expect the dockerfile to have been modified");
        assertEquals(strB.toString(), String.format("hello\n%s\nthis is a test\n", expectedReplacedData));
    }

    @Test
    public void testFindImagesAndFix_notModifiedPostData() throws Exception {
        gitHubUtil = mock(GitHubUtil.class);

        BufferedReader reader = mock(BufferedReader.class);
        GHContent content = mock(GHContent.class);
        when(content.getPath()).thenReturn("path");
        when(content.update(anyString(), anyString(), anyString())).thenReturn(null);

        when(reader.readLine()).thenReturn("hello", "FROM image:tag as builder",
                "this is a test", null);

        dockerfileGitHubUtil = new DockerfileGitHubUtil(gitHubUtil);

        StringBuilder strB = new StringBuilder();
        boolean modified = dockerfileGitHubUtil.rewriteDockerfile("image", "tag", reader, strB);

        assertFalse(modified, "Expected the dockerfile to not have changed.");
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
        gitHubUtil = mock(GitHubUtil.class);
        dockerfileGitHubUtil = new DockerfileGitHubUtil(gitHubUtil);
        assertEquals(dockerfileGitHubUtil.changeIfDockerfileBaseImageLine(img, tag, new StringBuilder(), line),
                expected);
    }

    @Test
    public void testChangeIfDockerfileBaseImageLine_modifyingStringBuilder() throws Exception {
        gitHubUtil = mock(GitHubUtil.class);
        dockerfileGitHubUtil = new DockerfileGitHubUtil(gitHubUtil);
        StringBuilder stringBuilder = new StringBuilder();
        String img = "image";
        String tag = "7357";
        dockerfileGitHubUtil.changeIfDockerfileBaseImageLine(img, tag, stringBuilder, "hello");
        dockerfileGitHubUtil.changeIfDockerfileBaseImageLine(img, tag, stringBuilder, "FROM image:blah");
        dockerfileGitHubUtil.changeIfDockerfileBaseImageLine(img, tag, stringBuilder, "world");
        dockerfileGitHubUtil.changeIfDockerfileBaseImageLine(img, tag, stringBuilder, "this is a test");
        assertEquals(stringBuilder.toString(), "hello\nFROM image:7357\nworld\nthis is a test\n");
    }

    @Test
    public void testDockerfileWithNoTag() {
        gitHubUtil = mock(GitHubUtil.class);
        dockerfileGitHubUtil = new DockerfileGitHubUtil(gitHubUtil);
        StringBuilder stringBuilder = new StringBuilder();
        String img = "image";
        String tag = "7357";
        dockerfileGitHubUtil.changeIfDockerfileBaseImageLine(img, tag, stringBuilder, "hello");
        dockerfileGitHubUtil.changeIfDockerfileBaseImageLine(img, tag, stringBuilder, "FROM image");
        dockerfileGitHubUtil.changeIfDockerfileBaseImageLine(img, tag, stringBuilder, "world");
        dockerfileGitHubUtil.changeIfDockerfileBaseImageLine(img, tag, stringBuilder, "this is a test");
        assertEquals(stringBuilder.toString(), "hello\nFROM image:7357\nworld\nthis is a test\n");
    }

    @DataProvider
    public Object[][] linesToSplitData() throws Exception {
        return new Object[][]{
                {"FROM dockerimage:3 # some comment", Arrays.asList("FROM", "dockerimage:3", "# some comment")},
                {"FROM    dockerimage:3      #   some   comment", Arrays.asList("FROM", "dockerimage:3", "#   some   comment")},
                {"FROM    dockerimage:3#   some   comment", Arrays.asList("FROM", "dockerimage:3", "#   some   comment")},
                {"FROM dockerimage", Arrays.asList("FROM", "dockerimage")},
                {"RUN something", Arrays.asList("RUN", "something")},
                {"", Collections.singletonList("")},
        };
    }

    @Test(dataProvider = "linesToSplitData")
    public void testLineToSplit(String input, List<String> expectedOutput) {
        assertEquals(DockerfileGitHubUtil.getLineParts(input), expectedOutput);
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
        gitHubUtil = mock(GitHubUtil.class);

        JsonElement json = new JsonParser().parse(storeContent);

        dockerfileGitHubUtil = new DockerfileGitHubUtil(gitHubUtil);
        String output = dockerfileGitHubUtil.getAndModifyJsonString(json, image, tag);
        assertEquals(output, expectedOutput);
    }

    @Test
    public void testCreatePullReq_Loop() throws Exception {
        gitHubUtil = mock(GitHubUtil.class);

        when(gitHubUtil.createPullReq(any(), anyString(), any(), anyString(), eq(Constants.PULL_REQ_ID))).thenReturn(-1, -1, -1, 0);
        dockerfileGitHubUtil = new DockerfileGitHubUtil(gitHubUtil);
        dockerfileGitHubUtil.createPullReq(new GHRepository(), "branch", new GHRepository(), "Automatic Dockerfile Image Updater");
        verify(gitHubUtil, times(4)).createPullReq(any(), anyString(), any(), anyString(), eq(Constants.PULL_REQ_ID));
    }

    @Test
    public void testCreatePullReq_Delete() throws Exception {
        gitHubUtil = mock(GitHubUtil.class);

        GHRepository forkRepo = mock(GHRepository.class);
        when(gitHubUtil.createPullReq(any(), anyString(), any(), anyString(), eq(Constants.PULL_REQ_ID))).thenReturn(1);
        doCallRealMethod().when(gitHubUtil).safeDeleteRepo(forkRepo);
        dockerfileGitHubUtil = new DockerfileGitHubUtil(gitHubUtil);
        dockerfileGitHubUtil.createPullReq(new GHRepository(), "branch", forkRepo, null);
        verify(gitHubUtil, times(1)).createPullReq(any(), anyString(), any(), anyString(), eq(Constants.PULL_REQ_ID));
        verify(forkRepo, times(1)).delete();
    }

}