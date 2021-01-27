/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.utils;

import com.salesforce.dockerfileimageupdate.model.GitForkBranch;
import com.salesforce.dockerfileimageupdate.model.PullRequestInfo;
import org.kohsuke.github.*;
import org.mockito.Mock;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.salesforce.dockerfileimageupdate.model.PullRequestInfo.DEFAULT_TITLE;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

/**
 * Created by minho.park on 7/27/16.
 */
public class DockerfileGitHubUtilTest {
    @Mock
    GitHubUtil gitHubUtil;

    private DockerfileGitHubUtil dockerfileGitHubUtil;

    @Test
    public void testGetGithubUtil() {
        gitHubUtil = mock(GitHubUtil.class);
        dockerfileGitHubUtil = new DockerfileGitHubUtil(gitHubUtil);
        assertEquals(dockerfileGitHubUtil.getGitHubUtil(), gitHubUtil);
    }

    @Test
    public void testParentIsForkedOnlyOnce() throws Exception {
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
        GHRepository returnRepo = dockerfileGitHubUtil.getOrCreateFork(parent);
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
        GHRepository returnRepo = dockerfileGitHubUtil.getOrCreateFork(parent);
        assertNull(returnRepo);
    }

    @Test
    public void testReturnPullRequestForBranch() {
        String imageName = "someimage";
        GHPullRequest ghPullRequest = mock(GHPullRequest.class);
        GHPullRequestQueryBuilder queryBuilder = getGHPullRequestQueryBuilder(imageName, Optional.of(ghPullRequest));
        GHRepository parent = mock(GHRepository.class);
        when(parent.queryPullRequests()).thenReturn(queryBuilder);
        GitForkBranch gitForkBranch = new GitForkBranch(imageName, "", null);


        gitHubUtil = mock(GitHubUtil.class);
        dockerfileGitHubUtil = new DockerfileGitHubUtil(gitHubUtil);
        assertEquals(dockerfileGitHubUtil.getPullRequestForImageBranch(parent, gitForkBranch), Optional.of(ghPullRequest));
    }

    @Test
    public void testNoPullRequestForBranch() {
        String imageName = "someimage";
        GHPullRequest ghPullRequest = mock(GHPullRequest.class);
        GHPullRequestQueryBuilder queryBuilder = getGHPullRequestQueryBuilder(imageName, Optional.empty());
        GHRepository parent = mock(GHRepository.class);
        when(parent.queryPullRequests()).thenReturn(queryBuilder);
        GitForkBranch gitForkBranch = new GitForkBranch(imageName, "", null);


        gitHubUtil = mock(GitHubUtil.class);
        dockerfileGitHubUtil = new DockerfileGitHubUtil(gitHubUtil);
        assertEquals(dockerfileGitHubUtil.getPullRequestForImageBranch(parent, gitForkBranch), Optional.empty());
    }

    private GHPullRequestQueryBuilder getGHPullRequestQueryBuilder(String imageName, Optional<GHPullRequest> ghPullRequest) {
        GHPullRequestQueryBuilder queryBuilder = mock(GHPullRequestQueryBuilder.class);
        when(queryBuilder.state(GHIssueState.OPEN)).thenReturn(queryBuilder);
        when(queryBuilder.head(imageName)).thenReturn(queryBuilder);
        PagedIterable<GHPullRequest> pullRequests = mock(PagedIterable.class);
        PagedIterator<GHPullRequest> pullRequestIterator = mock(PagedIterator.class);
        if (ghPullRequest.isPresent()) {
            when(pullRequestIterator.next()).thenReturn(ghPullRequest.get());
            when(pullRequestIterator.hasNext()).thenReturn(true);
        } else {
            when(pullRequestIterator.hasNext()).thenReturn(false);
        }
        when(pullRequests.iterator()).thenReturn(pullRequestIterator);
        when(queryBuilder.list()).thenReturn(pullRequests);
        return queryBuilder;
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
                { ":tag\t# comment # # # ", "newtag", "FROM image:newtag # comment # # # "},
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

    @Test
    public void testCreatePullReq_Loop() throws Exception {
        gitHubUtil = mock(GitHubUtil.class);

        when(gitHubUtil.createPullReq(any(), anyString(), any(), anyString(), eq(Constants.PULL_REQ_ID))).thenReturn(-1, -1, -1, 0);
        dockerfileGitHubUtil = new DockerfileGitHubUtil(gitHubUtil);
        PullRequestInfo pullRequestInfo = new PullRequestInfo(null, null, null, null);
        dockerfileGitHubUtil.createPullReq(new GHRepository(), "branch", new GHRepository(), pullRequestInfo);
        verify(gitHubUtil, times(4)).createPullReq(any(), anyString(), any(), eq(DEFAULT_TITLE), eq(Constants.PULL_REQ_ID));
    }

    @Test
    public void testCreatePullReq_Delete() throws Exception {
        gitHubUtil = mock(GitHubUtil.class);

        GHRepository forkRepo = mock(GHRepository.class);
        GHPullRequestQueryBuilder prBuilder = mock(GHPullRequestQueryBuilder.class);
        when(prBuilder.state(GHIssueState.OPEN)).thenReturn(prBuilder);
        PagedIterable<GHPullRequest> iteratable = mock(PagedIterable.class);
        when(prBuilder.list()).thenReturn(iteratable);
        PagedIterator<GHPullRequest> iterator = mock(PagedIterator.class);
        when(iterator.hasNext()).thenReturn(false);
        when(iteratable.iterator()).thenReturn(iterator);
        when(forkRepo.queryPullRequests()).thenReturn(prBuilder);
        when(gitHubUtil.createPullReq(any(), anyString(), any(), anyString(), eq(Constants.PULL_REQ_ID))).thenReturn(1);
        doCallRealMethod().when(gitHubUtil).safeDeleteRepo(forkRepo);
        dockerfileGitHubUtil = new DockerfileGitHubUtil(gitHubUtil);
        PullRequestInfo pullRequestInfo = new PullRequestInfo(null, null, null, null);
        dockerfileGitHubUtil.createPullReq(new GHRepository(), "branch", forkRepo, pullRequestInfo);
        verify(gitHubUtil, times(1)).createPullReq(any(), anyString(), any(), anyString(), eq(Constants.PULL_REQ_ID));
        verify(forkRepo, times(1)).delete();
    }
    @Test
    public void testGetGHContents() throws Exception {
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);

        GHContent content1 = mock(GHContent.class);
        GHContent content2 = mock(GHContent.class);
        GHContent content3 = mock(GHContent.class);

        PagedSearchIterable<GHContent> contentsWithImage = mock(PagedSearchIterable.class);
        when(contentsWithImage.getTotalCount()).thenReturn(3);

        PagedIterator<GHContent> contentsWithImageIterator = mock(PagedIterator.class);
        when(contentsWithImageIterator.hasNext()).thenReturn(true, true, true, false);
        when(contentsWithImageIterator.next()).thenReturn(content1, content2, content3, null);
        when(contentsWithImage.iterator()).thenReturn(contentsWithImageIterator);

        when(dockerfileGitHubUtil.findFilesWithImage(anyString(), eq("org"))).thenReturn(contentsWithImage);
        when(dockerfileGitHubUtil.getGHContents("org", "image")).thenCallRealMethod();

        assertEquals(dockerfileGitHubUtil.getGHContents("org", "image"), Optional.of(contentsWithImage));
    }

    @Test
    public void testGHContentsNoOutput() throws Exception {

        PagedSearchIterable<GHContent> contentsWithImage = mock(PagedSearchIterable.class);
        when(contentsWithImage.getTotalCount()).thenReturn(0);

        GitHubUtil gitHubUtil = mock(GitHubUtil.class);
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        when(dockerfileGitHubUtil.getGitHubUtil()).thenReturn(gitHubUtil);
        when(dockerfileGitHubUtil.findFilesWithImage(anyString(), eq("org"))).thenReturn(contentsWithImage);
        when(dockerfileGitHubUtil.getGHContents("org", "image")).thenCallRealMethod();

        assertEquals(dockerfileGitHubUtil.getGHContents("org", "image"), Optional.empty());
    }

    @Test
    public void testThisUserIsOwner() throws IOException {
        GitHubUtil gitHubUtil = mock(GitHubUtil.class);
        DockerfileGitHubUtil dockerfileGitHubUtil = new DockerfileGitHubUtil(gitHubUtil);
        String me = "me";
        GHRepository repo = mock(GHRepository.class);
        when(repo.getOwnerName()).thenReturn(me);
        GHMyself ghMyself = mock(GHMyself.class);
        when(ghMyself.getLogin()).thenReturn(me);
        when(gitHubUtil.getMyself()).thenReturn(ghMyself);

        assertTrue(dockerfileGitHubUtil.thisUserIsOwner(repo));
    }

    @Test(expectedExceptions = IOException.class)
    public void testThisUserIsOwnerCantFindMyself() throws IOException {
        GitHubUtil gitHubUtil = mock(GitHubUtil.class);
        DockerfileGitHubUtil dockerfileGitHubUtil = new DockerfileGitHubUtil(gitHubUtil);
        String me = "me";
        GHRepository repo = mock(GHRepository.class);
        when(repo.getOwnerName()).thenReturn(me);
        GHMyself ghMyself = mock(GHMyself.class);
        when(ghMyself.getLogin()).thenReturn(me);
        when(gitHubUtil.getMyself()).thenReturn(null);

        dockerfileGitHubUtil.thisUserIsOwner(repo);
    }

    @Test
    public void testCreateOrUpdateForkBranchToParentDefaultHasBranch() throws IOException, InterruptedException {
        GitHubUtil gitHubUtil = mock(GitHubUtil.class);
        DockerfileGitHubUtil dockerfileGitHubUtil = new DockerfileGitHubUtil(gitHubUtil);
        GHRepository parent = mock(GHRepository.class);
        String defaultBranch = "default";
        when(parent.getDefaultBranch()).thenReturn(defaultBranch);
        GHBranch parentBranch = mock(GHBranch.class);
        String sha = "abcdef";
        when(parentBranch.getSHA1()).thenReturn(sha);
        when(parent.getBranch(defaultBranch)).thenReturn(parentBranch);
        GHRepository fork = mock(GHRepository.class);
        GitForkBranch gitForkBranch = new GitForkBranch("imageName", "imageTag", null);
        when(gitHubUtil.repoHasBranch(fork, gitForkBranch.getBranchName())).thenReturn(true);
        GHRef returnedRef = mock(GHRef.class);
        when(fork.getRef(anyString())).thenReturn(returnedRef);

        dockerfileGitHubUtil.createOrUpdateForkBranchToParentDefault(parent, fork, gitForkBranch);

        verify(returnedRef, times(1)).updateTo(sha, true);
    }

    @Test
    public void testCreateOrUpdateForkBranchToParentDefaultDoesNotHaveBranch() throws IOException, InterruptedException {
        GitHubUtil gitHubUtil = mock(GitHubUtil.class);
        DockerfileGitHubUtil dockerfileGitHubUtil = new DockerfileGitHubUtil(gitHubUtil);
        GHRepository parent = mock(GHRepository.class);
        String defaultBranch = "default";
        when(parent.getDefaultBranch()).thenReturn(defaultBranch);
        GHBranch parentBranch = mock(GHBranch.class);
        String sha = "abcdef";
        when(parentBranch.getSHA1()).thenReturn(sha);
        when(parent.getBranch(defaultBranch)).thenReturn(parentBranch);
        GHRepository fork = mock(GHRepository.class);
        GitForkBranch gitForkBranch = new GitForkBranch("imageName", "imageTag", null);
        when(gitHubUtil.repoHasBranch(fork, gitForkBranch.getBranchName())).thenReturn(false);

        dockerfileGitHubUtil.createOrUpdateForkBranchToParentDefault(parent, fork, gitForkBranch);

        verify(fork, times(1)).createRef(anyString(), matches(sha));
    }
}
