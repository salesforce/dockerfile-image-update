/*
 * Copyright (c) 2017, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.subcommands.impl;

import com.google.common.collect.ImmutableMap;
import com.salesforce.dockerfileimageupdate.utils.DockerfileGithubUtil;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedIterator;
import org.kohsuke.github.PagedSearchIterable;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.times;

/**
 * Created by minho.park on 7/19/16.
 */
public class AllTest {
    @Test
    public void testForkRepositoriesFound() throws Exception {
        DockerfileGithubUtil dockerfileGithubUtil = Mockito.mock(DockerfileGithubUtil.class);

        GHRepository contentRepo1 = Mockito.mock(GHRepository.class);
        GHRepository contentRepo2 = Mockito.mock(GHRepository.class);
        GHRepository contentRepo3 = Mockito.mock(GHRepository.class);

        GHContent content1 = Mockito.mock(GHContent.class);
        Mockito.when(content1.getOwner()).thenReturn(contentRepo1);
        GHContent content2 = Mockito.mock(GHContent.class);
        Mockito.when(content2.getOwner()).thenReturn(contentRepo2);
        GHContent content3 = Mockito.mock(GHContent.class);
        Mockito.when(content3.getOwner()).thenReturn(contentRepo3);

        PagedSearchIterable<GHContent> contentsWithImage = Mockito.mock(PagedSearchIterable.class);

        PagedIterator<GHContent> contentsWithImageIterator = Mockito.mock(PagedIterator.class);
        Mockito.when(contentsWithImageIterator.hasNext()).thenReturn(true, true, true, false);
        Mockito.when(contentsWithImageIterator.next()).thenReturn(content1, content2, content3, null);
        Mockito.when(contentsWithImage.iterator()).thenReturn(contentsWithImageIterator);

        All all = new All();
        all.loadDockerfileGithubUtil(dockerfileGithubUtil);
        all.forkRepositoriesFound(new HashMap<>(), new HashMap<>(), contentsWithImage, "image");

        Mockito.verify(dockerfileGithubUtil, times(3)).checkFromParentAndFork(any());
    }

    @Test
    public void testChangeDockerfiles_returnIfNotFork() throws Exception {
        DockerfileGithubUtil dockerfileGithubUtil = Mockito.mock(DockerfileGithubUtil.class);
        GHRepository initialRepo = Mockito.mock(GHRepository.class);
        Mockito.when(initialRepo.isFork()).thenReturn(false);

        All all = new All();
        all.loadDockerfileGithubUtil(dockerfileGithubUtil);
        all.changeDockerfiles(null, null, null, initialRepo, "Automatic Dockerfile Image Updater", "");

        Mockito.verify(dockerfileGithubUtil, times(0)).getRepo(anyString());
    }

    @Test
    public void testChangeDockerfiles_returnIfNotDesiredParent() throws Exception {
        DockerfileGithubUtil dockerfileGithubUtil = Mockito.mock(DockerfileGithubUtil.class);

        GHRepository initialRepo = Mockito.mock(GHRepository.class);
        Mockito.when(initialRepo.isFork()).thenReturn(true);
        Mockito.when(initialRepo.getFullName()).thenReturn("repo");

        GHRepository retrievedRepo = Mockito.mock(GHRepository.class);
        GHRepository parentRepo = Mockito.mock(GHRepository.class);
        Mockito.when(parentRepo.getFullName()).thenReturn("test5");
        Mockito.when(retrievedRepo.getParent()).thenReturn(parentRepo);
        Mockito.when(retrievedRepo.getDefaultBranch()).thenReturn("branch");

        Mockito.when(dockerfileGithubUtil.getRepo(initialRepo.getFullName())).thenReturn(retrievedRepo);

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
        DockerfileGithubUtil dockerfileGithubUtil = Mockito.mock(DockerfileGithubUtil.class);

        GHRepository initialRepo = Mockito.mock(GHRepository.class);
        Mockito.when(initialRepo.isFork()).thenReturn(true);
        Mockito.when(initialRepo.getFullName()).thenReturn("repo");

        GHRepository retrievedRepo = Mockito.mock(GHRepository.class);
        GHRepository parentRepo = Mockito.mock(GHRepository.class);
        Mockito.when(parentRepo.getFullName()).thenReturn("test2");
        Mockito.when(retrievedRepo.getParent()).thenReturn(parentRepo);
        Mockito.when(retrievedRepo.getDefaultBranch()).thenReturn("branch");

        Mockito.when(dockerfileGithubUtil.getRepo(initialRepo.getFullName())).thenReturn(retrievedRepo);

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
}