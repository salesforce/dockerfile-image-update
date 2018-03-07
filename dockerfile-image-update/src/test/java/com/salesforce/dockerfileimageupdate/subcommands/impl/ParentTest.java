/*
 * Copyright (c) 2017, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.subcommands.impl;

import com.google.common.collect.ImmutableMap;
import com.salesforce.dockerfileimageupdate.utils.Constants;
import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;
import net.sourceforge.argparse4j.inf.Namespace;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedIterator;
import org.kohsuke.github.PagedSearchIterable;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/**
 * Created by minho.park on 7/19/16.
 */
public class ParentTest {

    @Test
    public void testGetGHContents() throws Exception {
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);

        Parent parent = new Parent();

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

        parent.loadDockerfileGithubUtil(dockerfileGitHubUtil);

        assertEquals(parent.getGHContents("org", "image"), contentsWithImage);
    }

    @Test
    public void testGHContentsNoOutput() throws Exception {
        Parent parent = new Parent();

        PagedSearchIterable<GHContent> contentsWithImage = mock(PagedSearchIterable.class);
        when(contentsWithImage.getTotalCount()).thenReturn(0);

        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        when(dockerfileGitHubUtil.findFilesWithImage(anyString(), eq("org"))).thenReturn(contentsWithImage);
        parent.loadDockerfileGithubUtil(dockerfileGitHubUtil);

        assertNull(parent.getGHContents("org", "image"));
    }

    @Test
    public void testForkRepositoriesFound() throws Exception {
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);

        GHRepository contentRepo1 = mock(GHRepository.class);
        when(contentRepo1.getFullName()).thenReturn("1");
        GHRepository contentRepo2 = mock(GHRepository.class);
        when(contentRepo2.getFullName()).thenReturn("2");
        GHRepository contentRepo3 = mock(GHRepository.class);
        when(contentRepo3.getFullName()).thenReturn("3");


        GHContent content1 = mock(GHContent.class);
        when(content1.getOwner()).thenReturn(contentRepo1);
        GHContent content2 = mock(GHContent.class);
        when(content2.getOwner()).thenReturn(contentRepo2);
        GHContent content3 = mock(GHContent.class);
        when(content3.getOwner()).thenReturn(contentRepo3);

        PagedSearchIterable<GHContent> contentsWithImage = mock(PagedSearchIterable.class);

        PagedIterator<GHContent> contentsWithImageIterator = mock(PagedIterator.class);
        when(contentsWithImageIterator.hasNext()).thenReturn(true, true, true, false);
        when(contentsWithImageIterator.next()).thenReturn(content1, content2, content3, null);
        when(contentsWithImage.iterator()).thenReturn(contentsWithImageIterator);

        Parent parent = new Parent();
        parent.loadDockerfileGithubUtil(dockerfileGitHubUtil);
        Map repoMap = new HashMap<>();
        parent.forkRepositoriesFound(repoMap, contentsWithImage);

        verify(dockerfileGitHubUtil, times(3)).checkFromParentAndFork(any());
        assertEquals(repoMap.size(), 3);
    }

    @Test
    public void testForkRepositoriesFound_forkRepoIsSkipped() throws Exception {
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);

        GHRepository contentRepo1 = mock(GHRepository.class);
        when(contentRepo1.getFullName()).thenReturn("1");

        GHContent content1 = mock(GHContent.class);
        when(content1.getOwner()).thenReturn(contentRepo1);
        when(contentRepo1.isFork()).thenReturn(true);

        PagedSearchIterable<GHContent> contentsWithImage = mock(PagedSearchIterable.class);

        PagedIterator<GHContent> contentsWithImageIterator = mock(PagedIterator.class);
        when(contentsWithImageIterator.hasNext()).thenReturn(true, false);
        when(contentsWithImageIterator.next()).thenReturn(content1, null);
        when(contentsWithImage.iterator()).thenReturn(contentsWithImageIterator);

        Parent parent = new Parent();
        parent.loadDockerfileGithubUtil(dockerfileGitHubUtil);
        Map repoMap = new HashMap<>();
        parent.forkRepositoriesFound(repoMap, contentsWithImage);

        verify(dockerfileGitHubUtil, never()).checkFromParentAndFork(any());
        assertEquals(repoMap.size(), 0);
    }

    @Test
    public void testChangeDockerfiles_returnIfNotFork() throws Exception {
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        GHRepository initialRepo = mock(GHRepository.class);
        when(initialRepo.isFork()).thenReturn(false);

        Parent parent = new Parent();
        parent.loadDockerfileGithubUtil(dockerfileGitHubUtil);
        parent.changeDockerfiles(null, null, initialRepo, "Automatic Dockerfile Image Updater");

        verify(dockerfileGitHubUtil, times(0)).getRepo(anyString());
    }

    @Test
    public void testChangeDockerfiles_returnIfNotDesiredParent() throws Exception {
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);

        GHRepository initialRepo = mock(GHRepository.class);
        when(initialRepo.isFork()).thenReturn(true);
        when(initialRepo.getFullName()).thenReturn("repo");

        GHRepository retrievedRepo = mock(GHRepository.class);
        GHRepository parentRepo = mock(GHRepository.class);
        when(parentRepo.getFullName()).thenReturn("test5");
        when(retrievedRepo.getParent()).thenReturn(parentRepo);
        when(retrievedRepo.getDefaultBranch()).thenReturn("branch");

        when(dockerfileGitHubUtil.getRepo(initialRepo.getFullName())).thenReturn(retrievedRepo);

        Map<String, String> parentToPath = ImmutableMap.of(
                "test1", "test",
                "test2", "correct",
                "test3", "test",
                "test4", "test");

        Parent parent = new Parent();
        parent.loadDockerfileGithubUtil(dockerfileGitHubUtil);
        parent.changeDockerfiles(null, parentToPath, initialRepo, "Automatic Dockerfile Image Updater");

        verify(dockerfileGitHubUtil, times(1)).getRepo(anyString());
        verify(dockerfileGitHubUtil, times(0))
                .tryRetrievingContent(eq(retrievedRepo), eq("correct"), eq("branch"));
        verify(dockerfileGitHubUtil, times(0))
                .modifyOnGithub(any(), eq("branch"), eq("image"), eq("tag"), anyString());
        verify(dockerfileGitHubUtil, times(0))
                .createPullReq(eq(parentRepo), eq("branch"), eq(retrievedRepo), anyString());
    }

    @Test
    public void testChangeDockerfiles_pullRequestCreation() throws Exception {
        Map<String, Object> nsMap = ImmutableMap.of(Constants.IMG, "image", Constants.TAG, "tag", Constants.STORE, "store");
        Namespace ns = new Namespace(nsMap);

        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);

        GHRepository initialRepo = mock(GHRepository.class);
        when(initialRepo.isFork()).thenReturn(true);
        when(initialRepo.getFullName()).thenReturn("repo");

        GHRepository retrievedRepo = mock(GHRepository.class);
        GHRepository parentRepo = mock(GHRepository.class);
        when(parentRepo.getFullName()).thenReturn("test2");
        when(retrievedRepo.getParent()).thenReturn(parentRepo);
        when(retrievedRepo.getDefaultBranch()).thenReturn("branch");

        when(dockerfileGitHubUtil.getRepo(initialRepo.getFullName())).thenReturn(retrievedRepo);

        Map<String, String> parentToPath = ImmutableMap.of(
                "test1", "test",
                "test2", "correct",
                "test3", "test",
                "test4", "test");

        Parent parent = new Parent();
        parent.loadDockerfileGithubUtil(dockerfileGitHubUtil);
        parent.changeDockerfiles(ns, parentToPath, initialRepo, "Automatic Dockerfile Image Updater");

        verify(dockerfileGitHubUtil, times(1)).getRepo(anyString());
        verify(dockerfileGitHubUtil, times(1))
                .tryRetrievingContent(eq(retrievedRepo), eq("correct"), eq("branch"));
        verify(dockerfileGitHubUtil, times(1))
                .modifyOnGithub(any(), eq("branch"), eq("image"), eq("tag"), anyString());
        verify(dockerfileGitHubUtil, times(1))
                .createPullReq(eq(parentRepo), eq("branch"), eq(retrievedRepo), anyString());
    }
}