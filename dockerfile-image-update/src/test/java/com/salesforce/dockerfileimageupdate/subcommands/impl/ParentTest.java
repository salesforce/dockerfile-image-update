/*
 * Copyright (c) 2017, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.subcommands.impl;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.salesforce.dockerfileimageupdate.utils.Constants;
import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;
import net.sourceforge.argparse4j.inf.Namespace;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedIterator;
import org.kohsuke.github.PagedSearchIterable;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Map;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

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
        Multimap<String, String> repoMap =  parent.forkRepositoriesFoundAndGetPathToDockerfiles(contentsWithImage);

        verify(dockerfileGitHubUtil, times(3)).closeOutdatedPullRequestAndFork(any());
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
        Multimap<String, String> repoMap = parent.forkRepositoriesFoundAndGetPathToDockerfiles(contentsWithImage);

        verify(dockerfileGitHubUtil, never()).closeOutdatedPullRequestAndFork(any());
        assertEquals(repoMap.size(), 0);
    }

    @Test
    public void testChangeDockerfiles_returnIfNotFork() throws Exception {
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        GHRepository currUserRepo = mock(GHRepository.class);
        when(currUserRepo.isFork()).thenReturn(false);

        Parent parent = new Parent();
        parent.loadDockerfileGithubUtil(dockerfileGitHubUtil);
        parent.changeDockerfiles(null, null, currUserRepo, new ArrayList<>());

        verify(dockerfileGitHubUtil, times(0)).getRepo(anyString());
    }

    @Test
    public void testChangeDockerfiles_returnIfNotDesiredParent() throws Exception {
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);

        GHRepository currUserRepo = mock(GHRepository.class);
        when(currUserRepo.isFork()).thenReturn(true);
        when(currUserRepo.getFullName()).thenReturn("forkedrepo5");

        GHRepository forkedRepo = mock(GHRepository.class);
        GHRepository parentRepo = mock(GHRepository.class);
        when(parentRepo.getFullName()).thenReturn("repo5");
        when(forkedRepo.getParent()).thenReturn(parentRepo);
        when(forkedRepo.getDefaultBranch()).thenReturn("branch");

        when(dockerfileGitHubUtil.getRepo(currUserRepo.getFullName())).thenReturn(forkedRepo);

        Multimap<String, String> pathToDockerfilesInParentRepo = HashMultimap.create();
        pathToDockerfilesInParentRepo.put("repo1", "df1");
        pathToDockerfilesInParentRepo.put("repo2", "df2");
        pathToDockerfilesInParentRepo.put("repo3", "df3");
        pathToDockerfilesInParentRepo.put("repo4", "df4");

        Parent parent = new Parent();
        parent.loadDockerfileGithubUtil(dockerfileGitHubUtil);
        parent.changeDockerfiles(null, pathToDockerfilesInParentRepo, currUserRepo, new ArrayList<>());

        verify(dockerfileGitHubUtil, times(1)).getRepo(eq(currUserRepo.getFullName()));
        verify(dockerfileGitHubUtil, times(0))
                .tryRetrievingContent(eq(forkedRepo), anyString(), anyString());
        verify(dockerfileGitHubUtil, times(0))
                .modifyOnGithub(any(), anyString(), anyString(), anyString(), anyString());
        verify(dockerfileGitHubUtil, times(0))
                .createPullReq(eq(parentRepo), anyString(), eq(forkedRepo), anyString());
    }

    @Test
    public void testChangeDockerfiles_returnWhenForkedRepoNotFound() throws Exception {

        GHRepository currUserRepo = mock(GHRepository.class);
        when(currUserRepo.isFork()).thenReturn(true);

        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        when(dockerfileGitHubUtil.getRepo(currUserRepo.getFullName())).thenThrow(FileNotFoundException.class);

        Parent parent = new Parent();
        parent.loadDockerfileGithubUtil(dockerfileGitHubUtil);
        parent.changeDockerfiles(null, null, currUserRepo, new ArrayList<>());

        Mockito.verify(dockerfileGitHubUtil, times(1)).getRepo(anyString());
        Mockito.verify(dockerfileGitHubUtil, times(0)).tryRetrievingContent(any(),
                anyString(), anyString());
        Mockito.verify(dockerfileGitHubUtil, times(0))
                .modifyOnGithub(any(), anyString(), anyString(), anyString(), anyString());
        Mockito.verify(dockerfileGitHubUtil, times(0)).createPullReq(any(), anyString(), any(), anyString());

    }

    @Test
    public void testChangeDockerfiles_pullRequestCreation() throws Exception {
        Map<String, Object> nsMap = ImmutableMap.of(Constants.IMG, "image", Constants.TAG, "tag", Constants.STORE, "store");
        Namespace ns = new Namespace(nsMap);

        GHRepository currUserRepo = mock(GHRepository.class);
        when(currUserRepo.isFork()).thenReturn(true);
        when(currUserRepo.getFullName()).thenReturn("forkedrepo");

        GHRepository forkedRepo = mock(GHRepository.class);
        GHRepository parentRepo = mock(GHRepository.class);
        when(parentRepo.getFullName()).thenReturn("repo2");
        when(forkedRepo.getParent()).thenReturn(parentRepo);
        when(forkedRepo.getFullName()).thenReturn("forkedrepo");
        when(forkedRepo.getDefaultBranch()).thenReturn("branch");

        Multimap<String, String> pathToDockerfilesInParentRepo = HashMultimap.create();
        pathToDockerfilesInParentRepo.put("repo1", "df1");
        pathToDockerfilesInParentRepo.put("repo2", "df2");
        pathToDockerfilesInParentRepo.put("repo3", "df3");
        pathToDockerfilesInParentRepo.put("repo4", "df4");

        GHContent content = mock(GHContent.class);
        when(content.getOwner()).thenReturn(forkedRepo);

        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        when(dockerfileGitHubUtil.getRepo(currUserRepo.getFullName())).thenReturn(forkedRepo);

        when(dockerfileGitHubUtil.tryRetrievingContent(forkedRepo, "df2",
                forkedRepo.getDefaultBranch())).thenReturn(content);

        Parent parent = new Parent();
        parent.loadDockerfileGithubUtil(dockerfileGitHubUtil);
        parent.changeDockerfiles(ns, pathToDockerfilesInParentRepo, currUserRepo, new ArrayList<>());

        Mockito.verify(dockerfileGitHubUtil, times(1)).getRepo(anyString());
        Mockito.verify(dockerfileGitHubUtil, times(1))
                .tryRetrievingContent(eq(forkedRepo), eq("df2"), eq("branch"));
        Mockito.verify(dockerfileGitHubUtil, times(1))
                .modifyOnGithub(eq(content), eq("branch"), eq("image"), eq("tag"), anyString());
        Mockito.verify(dockerfileGitHubUtil, times(1))
                .createPullReq(eq(parentRepo), eq("branch"), eq(forkedRepo), anyString());
    }

    @Test
    public void testOnePullRequestForMultipleDockerfilesInSameRepo() throws Exception {
        Map<String, Object> nsMap = ImmutableMap.of(Constants.IMG,
                "image", Constants.TAG,
                "tag", Constants.STORE,
                "store");
        Namespace ns = new Namespace(nsMap);

        Multimap<String, String> pathToDockerfilesInParentRepo = HashMultimap.create();
        pathToDockerfilesInParentRepo.put("repo1", "df11");
        pathToDockerfilesInParentRepo.put("repo1", "df12");
        pathToDockerfilesInParentRepo.put("repo3", "df3");
        pathToDockerfilesInParentRepo.put("repo4", "df4");

        GHRepository currUserRepo = mock(GHRepository.class);
        when(currUserRepo.isFork()).thenReturn(true);
        when(currUserRepo.getFullName()).thenReturn("forkedrepo");

        GHRepository forkedRepo = mock(GHRepository.class);
        GHRepository parentRepo = mock(GHRepository.class);
        when(parentRepo.getFullName()).thenReturn("repo1");
        when(forkedRepo.getParent()).thenReturn(parentRepo);
        when(forkedRepo.getDefaultBranch()).thenReturn("branch");

        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        when(dockerfileGitHubUtil.getRepo(currUserRepo.getFullName())).thenReturn(forkedRepo);
        GHContent forkedRepoContent1 = mock(GHContent.class);
        when(dockerfileGitHubUtil.tryRetrievingContent(eq(forkedRepo),
                eq("df11"), eq("branch"))).thenReturn(forkedRepoContent1);
        GHContent forkedRepoContent2 = mock(GHContent.class);
        when(dockerfileGitHubUtil.tryRetrievingContent(eq(forkedRepo),
                eq("df12"), eq("branch"))).thenReturn(forkedRepoContent2);

        Parent parent = new Parent();
        parent.loadDockerfileGithubUtil(dockerfileGitHubUtil);
        parent.changeDockerfiles(ns, pathToDockerfilesInParentRepo, currUserRepo, new ArrayList<>());

        // Get repo only once
        Mockito.verify(dockerfileGitHubUtil, times(1)).getRepo(anyString());

        // Both Dockerfiles retrieved from the same repo
        Mockito.verify(dockerfileGitHubUtil, times(1)).tryRetrievingContent(eq(forkedRepo),
                eq("df11"), eq("branch"));
        Mockito.verify(dockerfileGitHubUtil, times(1)).tryRetrievingContent(eq(forkedRepo),
                eq("df12"), eq("branch"));

        // Both Dockerfiles modified
        Mockito.verify(dockerfileGitHubUtil, times(2))
                .modifyOnGithub(any(), eq("branch"), eq("image"), eq("tag"), anyString());

        // Only one PR created on the repo with changes to both Dockerfiles.
        Mockito.verify(dockerfileGitHubUtil, times(1)).createPullReq(eq(parentRepo),
                eq("branch"), eq(forkedRepo), anyString());
    }

    @Test
    public void testNoPullRequestForMissingDockerfile() throws Exception {
        Map<String, Object> nsMap = ImmutableMap.of(Constants.IMG,
                "image", Constants.TAG,
                "tag", Constants.STORE,
                "store");
        Namespace ns = new Namespace(nsMap);

        Multimap<String, String> pathToDockerfilesInParentRepo = HashMultimap.create();
        pathToDockerfilesInParentRepo.put("repo1", "missing_df");

        GHRepository currUserRepo = mock(GHRepository.class);
        when(currUserRepo.isFork()).thenReturn(true);
        when(currUserRepo.getFullName()).thenReturn("forkedrepo");

        GHRepository forkedRepo = mock(GHRepository.class);
        GHRepository parentRepo = mock(GHRepository.class);
        when(parentRepo.getFullName()).thenReturn("repo1");
        when(forkedRepo.getParent()).thenReturn(parentRepo);
        when(forkedRepo.getFullName()).thenReturn("forkedrepo");
        when(forkedRepo.getDefaultBranch()).thenReturn("branch");

        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        when(dockerfileGitHubUtil.getRepo(currUserRepo.getFullName())).thenReturn(forkedRepo);
        // Dockerfile not found anymore when trying to retrieve contents from the forked repo.
        when(dockerfileGitHubUtil.tryRetrievingContent(eq(forkedRepo),
                eq("missing_df"), eq("branch"))).thenReturn(null);

        Parent parent = new Parent();
        parent.loadDockerfileGithubUtil(dockerfileGitHubUtil);
        parent.changeDockerfiles(ns, pathToDockerfilesInParentRepo, currUserRepo, new ArrayList<>());

        // fetch repo
        Mockito.verify(dockerfileGitHubUtil, times(1)).getRepo(anyString());

        // trying to retrieve Dockerfile
        Mockito.verify(dockerfileGitHubUtil, times(1)).tryRetrievingContent(eq(forkedRepo),
                eq("missing_df"), eq("branch"));

        // missing Dockerfile, so skipping modify and create PR
        Mockito.verify(dockerfileGitHubUtil, times(0))
                .modifyOnGithub(any(), anyString(), anyString(), anyString(), anyString());
        Mockito.verify(dockerfileGitHubUtil, times(0)).createPullReq(eq(parentRepo),
                anyString(), eq(forkedRepo), anyString());
    }

    @Test
    public void testPullRequestToAForkIsUnSupported() throws Exception {

        GHRepository parentRepo = mock(GHRepository.class);
        // When the repo is a fork then skip it.
        when(parentRepo.isFork()).thenReturn(true);
        GHContent content = mock(GHContent.class);
        when(content.getOwner()).thenReturn(parentRepo);

        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        PagedSearchIterable<GHContent> contentsWithImage = mock(PagedSearchIterable.class);
        PagedIterator<GHContent> contentsWithImageIterator = mock(PagedIterator.class);
        when(contentsWithImageIterator.hasNext()).thenReturn(true, false);
        when(contentsWithImageIterator.next()).thenReturn(content, null);
        when(contentsWithImage.iterator()).thenReturn(contentsWithImageIterator);

        Parent parent = new Parent();
        parent.loadDockerfileGithubUtil(dockerfileGitHubUtil);
        Multimap<String, String> pathToDockerfiles = parent.forkRepositoriesFoundAndGetPathToDockerfiles(contentsWithImage);
        assertTrue(pathToDockerfiles.isEmpty());
        Mockito.verify(dockerfileGitHubUtil, times(0)).closeOutdatedPullRequestAndFork(any());
    }
}