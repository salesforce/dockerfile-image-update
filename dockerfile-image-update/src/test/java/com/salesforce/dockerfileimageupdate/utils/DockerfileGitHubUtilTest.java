/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.utils;

import com.google.common.collect.*;
import com.salesforce.dockerfileimageupdate.model.*;
import net.sourceforge.argparse4j.inf.*;
import org.kohsuke.github.*;
import org.mockito.*;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Map;
import java.util.Iterator;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

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
        GHBranch branch = mock(GHBranch.class);
        when(parent.getBranch(anyString())).thenReturn(branch);
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
        Map<String, Boolean> orgs = Collections.unmodifiableMap(Collections.singletonMap(org, true));
        dockerfileGitHubUtil.findFilesWithImage(query, orgs, 1000);
    }

    @Test
    public void testFindFiles() throws Exception {
        gitHubUtil = mock(GitHubUtil.class);

        GHContentSearchBuilder ghContentSearchBuilder = mock(GHContentSearchBuilder.class);
        PagedSearchIterable<GHContent> contentsWithImage = mock(PagedSearchIterable.class);
        List<PagedSearchIterable<GHContent>> contentsWithImageList = new ArrayList<>();
        contentsWithImageList.add(contentsWithImage);
        Optional<List<PagedSearchIterable<GHContent>>> optionalContentsWithImageList = Optional.of(contentsWithImageList);
        when(contentsWithImage.getTotalCount()).thenReturn(100);

        when(ghContentSearchBuilder.list()).thenReturn(contentsWithImage);
        when(gitHubUtil.startSearch()).thenReturn(ghContentSearchBuilder);

        dockerfileGitHubUtil = new DockerfileGitHubUtil(gitHubUtil);
        Map<String, Boolean> orgs = Collections.unmodifiableMap(Collections.singletonMap("test", true));
        assertEquals(dockerfileGitHubUtil.findFilesWithImage("test", orgs, 1000), optionalContentsWithImageList);
    }

    @Test
    public void testFindFilesWithMoreThan1000Results() throws Exception {
        gitHubUtil = mock(GitHubUtil.class);
        GHContentSearchBuilder ghContentSearchBuilder = mock(GHContentSearchBuilder.class);

        GHRepository contentRepo1 = mock(GHRepository.class);
        when(contentRepo1.getOwnerName()).thenReturn("org-1");
        GHRepository contentRepo2 = mock(GHRepository.class);
        when(contentRepo2.getOwnerName()).thenReturn("org-2");
        GHRepository contentRepo3 = mock(GHRepository.class);
        when(contentRepo3.getOwnerName()).thenReturn("org-3");


        GHContent content1 = mock(GHContent.class);
        when(content1.getOwner()).thenReturn(contentRepo1);
        GHContent content2 = mock(GHContent.class);
        when(content2.getOwner()).thenReturn(contentRepo2);
        GHContent content3 = mock(GHContent.class);
        when(content3.getOwner()).thenReturn(contentRepo3);
        List<GHContent> ghContentList = Arrays.asList(content1, content2, content3);

        PagedSearchIterable<GHContent> contentsWithImage = mock(PagedSearchIterable.class);

        List<PagedSearchIterable<GHContent>> contentsWithImageList = Arrays.asList(contentsWithImage, contentsWithImage, contentsWithImage);
        Optional<List<PagedSearchIterable<GHContent>>> optionalContentsWithImageList = Optional.of(contentsWithImageList);

        when(contentsWithImage.toList()).thenReturn(ghContentList);

        //org-1 has 1001 matches, org-2 1000 matches and org-3 1000 matches
        when(contentsWithImage.getTotalCount()).thenReturn(3001, 1001, 2000, 1000, 1000);
        when(ghContentSearchBuilder.list()).thenReturn(contentsWithImage);
        when(gitHubUtil.startSearch()).thenReturn(ghContentSearchBuilder);

        dockerfileGitHubUtil = new DockerfileGitHubUtil(gitHubUtil);
        Map<String, Boolean> orgsToIncludeOrExclude = new HashMap<>();
        orgsToIncludeOrExclude.put(null, true);

        dockerfileGitHubUtil = new DockerfileGitHubUtil(gitHubUtil);
        when(dockerfileGitHubUtil.getOrgNameWithMaximumHits(contentsWithImage)).thenReturn("org-1", "org-2", "org-3");
        assertEquals(dockerfileGitHubUtil.findFilesWithImage("test", orgsToIncludeOrExclude, 1000), optionalContentsWithImageList);
    }

    @Test
    public void getSearchResultsExcludingOrgWithMostHits() throws Exception {
        gitHubUtil = mock(GitHubUtil.class);
        GHContentSearchBuilder ghContentSearchBuilder = mock(GHContentSearchBuilder.class);

        GHRepository contentRepo1 = mock(GHRepository.class);
        when(contentRepo1.getOwnerName()).thenReturn("org-1");
        GHRepository contentRepo2 = mock(GHRepository.class);
        when(contentRepo2.getOwnerName()).thenReturn("org-2");
        GHRepository contentRepo3 = mock(GHRepository.class);
        when(contentRepo3.getOwnerName()).thenReturn("org-3");


        GHContent content1 = mock(GHContent.class);
        when(content1.getOwner()).thenReturn(contentRepo1);
        GHContent content2 = mock(GHContent.class);
        when(content2.getOwner()).thenReturn(contentRepo2);
        GHContent content3 = mock(GHContent.class);
        when(content3.getOwner()).thenReturn(contentRepo3);
        PagedSearchIterable<GHContent> contentsWithImage = mock(PagedSearchIterable.class);

        List<GHContent> ghContentList = Arrays.asList(content1, content2, content3);

        when(contentsWithImage.toList()).thenReturn(ghContentList);

        when(contentsWithImage.getTotalCount()).thenReturn(100);
        when(ghContentSearchBuilder.list()).thenReturn(contentsWithImage);
        when(gitHubUtil.startSearch()).thenReturn(ghContentSearchBuilder);

        dockerfileGitHubUtil = new DockerfileGitHubUtil(gitHubUtil);
        Map<String, Boolean> orgsToIncludeOrExclude = new HashMap<>();

        assertEquals((dockerfileGitHubUtil.getSearchResultsExcludingOrgWithMostHits("image", contentsWithImage, orgsToIncludeOrExclude, 1000)).get().size(), 2);
        //This check ensures that the parameter passed to the method is not modified. Instead, the method creates a local copy of the map and modifies that.
        assertEquals(orgsToIncludeOrExclude.size(), 0);
    }

    @Test
    public void getOrgNameWithMaximumHits() throws Exception {
        gitHubUtil = mock(GitHubUtil.class);
        GHRepository contentRepo1 = mock(GHRepository.class);
        when(contentRepo1.getOwnerName()).thenReturn("org-1");
        GHRepository contentRepo2 = mock(GHRepository.class);
        when(contentRepo2.getOwnerName()).thenReturn("org-1");
        GHRepository contentRepo3 = mock(GHRepository.class);
        when(contentRepo3.getOwnerName()).thenReturn("org-2");


        GHContent content1 = mock(GHContent.class);
        when(content1.getOwner()).thenReturn(contentRepo1);
        GHContent content2 = mock(GHContent.class);
        when(content2.getOwner()).thenReturn(contentRepo2);
        GHContent content3 = mock(GHContent.class);
        when(content3.getOwner()).thenReturn(contentRepo3);
        PagedSearchIterable<GHContent> contentsWithImage = mock(PagedSearchIterable.class);

        List<GHContent> ghContentList = Arrays.asList(content1, content2, content3);

        when(contentsWithImage.toList()).thenReturn(ghContentList);

        dockerfileGitHubUtil = new DockerfileGitHubUtil(gitHubUtil);

        assertEquals((dockerfileGitHubUtil.getOrgNameWithMaximumHits(contentsWithImage)), "org-1");
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
        dockerfileGitHubUtil.modifyOnGithubRecursive(repo, content, branch, img, tag, "");

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
        dockerfileGitHubUtil.findImagesAndFix(content, branch, searchImage, newTag, "", reader, "");
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
        dockerfileGitHubUtil.findImagesAndFix(content, branch, searchImage, newTag, "", reader, "");
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
        dockerfileGitHubUtil.rewriteDockerfile("image", "newtag", reader, strB, "");

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
        boolean modified = dockerfileGitHubUtil.rewriteDockerfile("image", updatedTag, reader, strB, "");

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
        boolean modified = dockerfileGitHubUtil.rewriteDockerfile("image", "tag", reader, strB, "");

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
                {"image3", "7", "FROM image3  ",  true},
                {"image3", "7", "\tFROM image3\t", true},
                {"image7", "98", "FROM image7:4", true},
                {"image7", "98", "FROM image7: 4", true},
                {"image124516418023_1085-1-1248571", "7357",
                        "FROM image124516418023_1085-1-1248571:18026809126359806124890356219518632048125", true},
                {"image", "1234",
                        "FROM image:1234", false},
                {"image", "1234",
                        "FROM image:1234", false},
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
    public void testDockerfileWithNoTag() throws IOException {
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
        List<PagedSearchIterable<GHContent>> contentsWithImageList = Collections.singletonList(contentsWithImage);
        Optional<List<PagedSearchIterable<GHContent>>> optionalContentsWithImageList = Optional.of(contentsWithImageList);
        when(contentsWithImage.getTotalCount()).thenReturn(3);

        PagedIterator<GHContent> contentsWithImageIterator = mock(PagedIterator.class);
        when(contentsWithImageIterator.hasNext()).thenReturn(true, true, true, false);
        when(contentsWithImageIterator.next()).thenReturn(content1, content2, content3, null);
        when(contentsWithImage.iterator()).thenReturn(contentsWithImageIterator);
        Map<String, Boolean> orgs = Collections.unmodifiableMap(Collections.singletonMap("org", true));
        when(dockerfileGitHubUtil.findFilesWithImage(anyString(), eq(orgs), anyInt())).thenReturn(optionalContentsWithImageList);
        when(dockerfileGitHubUtil.getGHContents("org", "image", 1000)).thenCallRealMethod();

        assertEquals(dockerfileGitHubUtil.getGHContents("org", "image", 1000), Optional.of(contentsWithImageList));
    }

    @Test
    public void testGHContentsNoOutput() throws Exception {

        PagedSearchIterable<GHContent> contentsWithImage = mock(PagedSearchIterable.class);
        List<PagedSearchIterable<GHContent>> contentsWithImageList = Collections.singletonList(contentsWithImage);
        Optional<List<PagedSearchIterable<GHContent>>> optionalContentsWithImageList = Optional.of(contentsWithImageList);
        when(contentsWithImage.getTotalCount()).thenReturn(0);

        GitHubUtil gitHubUtil = mock(GitHubUtil.class);
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        when(dockerfileGitHubUtil.getGitHubUtil()).thenReturn(gitHubUtil);
        Map<String, Boolean> orgs = Collections.unmodifiableMap(Collections.singletonMap("org", true));
        when(dockerfileGitHubUtil.findFilesWithImage(anyString(), eq(orgs), anyInt())).thenReturn(optionalContentsWithImageList);
        when(dockerfileGitHubUtil.getGHContents("org", "image", 1000)).thenCallRealMethod();

        assertEquals(dockerfileGitHubUtil.getGHContents("org", "image", 1000), Optional.empty());
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

    @Test
    public void testOnePullRequestForMultipleDockerfilesInSameRepo() throws Exception {
        Map<String, Object> nsMap = ImmutableMap.of(Constants.IMG,
                "image", Constants.TAG,
                "tag", Constants.STORE,
                "store");
        Namespace ns = new Namespace(nsMap);
        GitForkBranch gitForkBranch = new GitForkBranch("image", "tag", null);
        Multimap<String, GitHubContentToProcess> pathToDockerfilesInParentRepo = HashMultimap.create();
        pathToDockerfilesInParentRepo.put("repo1", new GitHubContentToProcess(null, null, "df11"));
        pathToDockerfilesInParentRepo.put("repo1", new GitHubContentToProcess(null, null, "df12"));
        pathToDockerfilesInParentRepo.put("repo3", new GitHubContentToProcess(null, null, "df3"));
        pathToDockerfilesInParentRepo.put("repo4", new GitHubContentToProcess(null, null, "df4"));

        GHRepository forkedRepo = mock(GHRepository.class);
        when(forkedRepo.isFork()).thenReturn(true);
        when(forkedRepo.getFullName()).thenReturn("forkedrepo");

        GHRepository parentRepo = mock(GHRepository.class);
        when(parentRepo.getFullName()).thenReturn("repo1");
        when(forkedRepo.getParent()).thenReturn(parentRepo);

        //GHRepository parent = mock(GHRepository.class);
        String defaultBranch = "default";
        when(parentRepo.getDefaultBranch()).thenReturn(defaultBranch);
        GHBranch parentBranch = mock(GHBranch.class);
        String sha = "abcdef";
        when(parentBranch.getSHA1()).thenReturn(sha);
        when(parentRepo.getBranch(defaultBranch)).thenReturn(parentBranch);

        gitHubUtil = mock(GitHubUtil.class);
        dockerfileGitHubUtil = Mockito.spy(new DockerfileGitHubUtil(gitHubUtil));
        //when(dockerfileGitHubUtil.getPullRequestForImageBranch(any(), any())).thenReturn
        // (Optional.empty());
        //when(dockerfileGitHubUtil.getRepo(forkedRepo.getFullName())).thenReturn(forkedRepo);
        GHContent forkedRepoContent1 = mock(GHContent.class);
        when(gitHubUtil.tryRetrievingContent(eq(forkedRepo),
                eq("df11"), eq("image-tag"))).thenReturn(forkedRepoContent1);
        GHContent forkedRepoContent2 = mock(GHContent.class);
        when(gitHubUtil.tryRetrievingContent(eq(forkedRepo),
                eq("df12"), eq("image-tag"))).thenReturn(forkedRepoContent2);
        doNothing().when(dockerfileGitHubUtil).modifyOnGithub(any(), eq("image-tag"), eq("image")
                , eq("tag"), anyString(), anyString());

        dockerfileGitHubUtil.changeDockerfiles(ns, pathToDockerfilesInParentRepo,
                new GitHubContentToProcess(forkedRepo, parentRepo, ""), new ArrayList<>(), gitForkBranch);

        // Both Dockerfiles retrieved from the same repo
        verify(dockerfileGitHubUtil, times(1)).tryRetrievingContent(eq(forkedRepo),
                eq("df11"), eq("image-tag"));
        verify(dockerfileGitHubUtil, times(1)).tryRetrievingContent(eq(forkedRepo),
                eq("df12"), eq("image-tag"));

        // Both Dockerfiles modified
        verify(dockerfileGitHubUtil, times(2))
                .modifyOnGithub(any(), eq("image-tag"), eq("image"), eq("tag"), anyString(), anyString());

        // Only one PR created on the repo with changes to both Dockerfiles.
        verify(dockerfileGitHubUtil, times(1)).createPullReq(eq(parentRepo),
                eq("image-tag"), eq(forkedRepo), any());
    }

    @DataProvider
    public Object[][] fromInstructionWithIgnoreStringData() {
        return new Object[][] {
                { "# no-dfiu\nFROM image:original", "", "# no-dfiu\nFROM image:original\n"},
                { "# no-dfiu\nFROM image:original", "dont-ignore", "# no-dfiu\nFROM image:changed\n"},
                { "# ignore-pr\nFROM image:original", "dont-ignore", "# ignore-pr\nFROM image:changed\n"},
                { "# ignore-pr\nFROM image:original", "ignore-pr", "# ignore-pr\nFROM image:original\n"},
                { "# ignore-pr\nFROM image:original", "", "# ignore-pr\nFROM image:changed\n"},
                { "# no-dfiu\nFROM image:original\nFROM image:abcd", "", "# no-dfiu\nFROM image:original\nFROM image:changed\n"},
                { "# no-dfiu\nFROM image:original\n# no-dfiu\nFROM image:abcd", "", "# no-dfiu\nFROM image:original\n# no-dfiu\nFROM image:abcd\n"},
                { "#no-dfiu\nFROM image:original", "", "#no-dfiu\nFROM image:original\n"},
        };
    }

    @Test(dataProvider = "fromInstructionWithIgnoreStringData")
    public void testDockerfileWithIgnoreImageString(String dockerfileLines, String ignoreImageString, String outputLines) throws IOException {
        gitHubUtil = mock(GitHubUtil.class);
        dockerfileGitHubUtil = new DockerfileGitHubUtil(gitHubUtil);
        StringBuilder stringBuilder = new StringBuilder();
        String img = "image";
        String tag = "changed";
        InputStream stream = new ByteArrayInputStream(dockerfileLines.getBytes(Charset.forName("UTF-8")));
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        dockerfileGitHubUtil.rewriteDockerfile(img, tag, reader, stringBuilder, ignoreImageString);
        assertEquals(stringBuilder.toString(), outputLines, "");
    }

    @DataProvider
    public Object[][] commentDataNoDfiu() {
        return new Object[][] {
                {"#FROM alpine:latest", "",         false},
                {"#no-dfiu",            "",         true},
                {"# no-dfiu",           "",         true},
                {"\t# no-dfiu",         "",         true},
                {"\t# no-dfiu # # # ",  "",         false},
                {"#\n",                 "",         false},
                {"# :no-dfiu",          "",         false},
                {"# no-dfiu added comments for ignoring dfiu PR", "",       false},
                {"#FROM a:b",           "",                                 false},
                {"#ignore-pr",          "ignore-pr",                        true},
                {"#ignore-pr",          "ignore pr",                        false}
        };
    }

    @Test(dataProvider = "commentDataNoDfiu")
    public void testCommentsWithNoDfiuParsedCorrectly(String line, String ignoreImageString, Boolean expectedResult) throws IOException {
        gitHubUtil = mock(GitHubUtil.class);
        dockerfileGitHubUtil = new DockerfileGitHubUtil(gitHubUtil);
        assertEquals(dockerfileGitHubUtil.ignorePRCommentPresent(line, ignoreImageString), expectedResult);
    }
}
