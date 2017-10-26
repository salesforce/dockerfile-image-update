/*
 * Copyright (c) 2017, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.subcommands.impl;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.salesforce.dockerfileimageupdate.utils.DockerfileGithubUtil;
import org.kohsuke.github.*;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertNotNull;

/**
 * Created by minho.park on 7/19/16.
 */
public class AllTest {
    @Test
    public void testForkRepositoriesFound() throws Exception {
        DockerfileGithubUtil dockerfileGithubUtil = mock(DockerfileGithubUtil.class);

        GHRepository contentRepo1 = mock(GHRepository.class);
        GHRepository contentRepo2 = mock(GHRepository.class);
        GHRepository contentRepo3 = mock(GHRepository.class);

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

        All all = new All();
        all.loadDockerfileGithubUtil(dockerfileGithubUtil);
        all.forkRepositoriesFound(new HashMap<>(), new HashMap<>(), contentsWithImage, "image");

        Mockito.verify(dockerfileGithubUtil, times(3)).checkFromParentAndFork(any());
    }

    @Test
    public void testChangeDockerfiles_returnIfNotFork() throws Exception {
        DockerfileGithubUtil dockerfileGithubUtil = mock(DockerfileGithubUtil.class);
        GHRepository initialRepo = mock(GHRepository.class);
        when(initialRepo.isFork()).thenReturn(false);

        All all = new All();
        all.loadDockerfileGithubUtil(dockerfileGithubUtil);
        all.changeDockerfiles(null, null, null, initialRepo, "Automatic Dockerfile Image Updater", "");

        Mockito.verify(dockerfileGithubUtil, times(0)).getRepo(anyString());
    }

    @Test
    public void testChangeDockerfiles_returnIfNotDesiredParent() throws Exception {
        DockerfileGithubUtil dockerfileGithubUtil = mock(DockerfileGithubUtil.class);

        GHRepository initialRepo = mock(GHRepository.class);
        when(initialRepo.isFork()).thenReturn(true);
        when(initialRepo.getFullName()).thenReturn("repo");

        GHRepository retrievedRepo = mock(GHRepository.class);
        GHRepository parentRepo = mock(GHRepository.class);
        when(parentRepo.getFullName()).thenReturn("test5");
        when(retrievedRepo.getParent()).thenReturn(parentRepo);
        when(retrievedRepo.getDefaultBranch()).thenReturn("branch");

        when(dockerfileGithubUtil.getRepo(initialRepo.getFullName())).thenReturn(retrievedRepo);

        Map<String, String> parentToPath = ImmutableMap.of(
                "test1", "test",
                "test2", "correct",
                "test3", "test",
                "test4", "test");

        All all = new All();
        all.loadDockerfileGithubUtil(dockerfileGithubUtil);
        all.changeDockerfiles(parentToPath, null, null, initialRepo, "Automatic Dockerfile Image Updater", "");

        Mockito.verify(dockerfileGithubUtil, times(1)).getRepo(anyString());
        Mockito.verify(dockerfileGithubUtil, times(0)).tryRetrievingContent(eq(retrievedRepo),
                eq("correct"), eq("branch"));
        Mockito.verify(dockerfileGithubUtil, times(0))
                .modifyOnGithub(any(), eq("branch"), eq("image"), eq("tag"), anyString());
        Mockito.verify(dockerfileGithubUtil, times(0)).createPullReq(eq(parentRepo), eq("branch"),
                eq(retrievedRepo), anyString());
    }

    @Test
    public void testChangeDockerfiles_pullRequestCreation() throws Exception {
        DockerfileGithubUtil dockerfileGithubUtil = mock(DockerfileGithubUtil.class);

        GHRepository initialRepo = mock(GHRepository.class);
        when(initialRepo.isFork()).thenReturn(true);
        when(initialRepo.getFullName()).thenReturn("repo");

        GHRepository retrievedRepo = mock(GHRepository.class);
        GHRepository parentRepo = mock(GHRepository.class);
        when(parentRepo.getFullName()).thenReturn("test2");
        when(retrievedRepo.getParent()).thenReturn(parentRepo);
        when(retrievedRepo.getDefaultBranch()).thenReturn("branch");

        when(dockerfileGithubUtil.getRepo(initialRepo.getFullName())).thenReturn(retrievedRepo);

        Map<String, String> parentToPath = ImmutableMap.of(
                "test1", "test",
                "test2", "correct",
                "test3", "test",
                "test4", "test");

        Map<String, String> parentToImage = ImmutableMap.of(
                "test1", "image1",
                "test2", "image2",
                "test3", "image3",
                "test4", "image4");

        Map<String, String> imageToTagMap = ImmutableMap.of(
                "image1", "tag1",
                "image2", "tag2",
                "image3", "tag3",
                "image4", "tag4");

        All all = new All();
        all.loadDockerfileGithubUtil(dockerfileGithubUtil);
        all.changeDockerfiles(parentToPath, parentToImage, imageToTagMap,
                initialRepo, "Automatic Dockerfile Image Updater", "");

        Mockito.verify(dockerfileGithubUtil, times(1)).getRepo(anyString());
        Mockito.verify(dockerfileGithubUtil, times(1)).tryRetrievingContent(eq(retrievedRepo),
                eq("correct"), eq("branch"));
        Mockito.verify(dockerfileGithubUtil, times(1))
                .modifyOnGithub(any(), eq("branch"), eq("image2"), eq("tag2"), anyString());
        Mockito.verify(dockerfileGithubUtil, times(1)).createPullReq(eq(parentRepo), eq("branch"),
                eq(retrievedRepo), anyString());
    }

    @Test
    public void testParseStoreToImagesMap() throws Exception {
        DockerfileGithubUtil dockerfileGithubUtil = mock(DockerfileGithubUtil.class);
        when(dockerfileGithubUtil.getMyself()).thenReturn(mock(GHMyself.class));
        when(dockerfileGithubUtil.getRepo(anyString())).thenReturn(mock(GHRepository.class));
        GHContent mockContent = mock(GHContent.class);
        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        when(mockContent.read()).thenReturn(classloader.getResourceAsStream("image-store-sample.json"));
        when(dockerfileGithubUtil.tryRetrievingContent(any(GHRepository.class), anyString(), anyString())).thenReturn(mockContent);

        All all = new All();
        all.loadDockerfileGithubUtil(dockerfileGithubUtil);
        Set<Map.Entry<String, JsonElement>> imageSet = all.parseStoreToImagesMap("testStore");
        assertNotNull(imageSet);
    }
}