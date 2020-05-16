/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.subcommands.impl;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.salesforce.dockerfileimageupdate.model.GitHubContentToProcess;
import com.salesforce.dockerfileimageupdate.utils.Constants;
import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;
import net.sourceforge.argparse4j.inf.Namespace;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedIterator;
import org.kohsuke.github.PagedSearchIterable;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Matchers.*;
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

        when(dockerfileGitHubUtil.getOrCreateFork(Mockito.any())).thenReturn(new GHRepository());
        when(dockerfileGitHubUtil.getRepo(any())).thenReturn(mock(GHRepository.class));

        Parent parent = new Parent();
        parent.loadDockerfileGithubUtil(dockerfileGitHubUtil);
        Multimap<String, GitHubContentToProcess> repoMap =  parent.forkRepositoriesFoundAndGetPathToDockerfiles(contentsWithImage);

        verify(dockerfileGitHubUtil, times(3)).getOrCreateFork(any());
        assertEquals(repoMap.size(), 3);
    }

    @Test
    public void forkRepositoriesFoundAndGetPathToDockerfiles_unableToforkRepo() throws Exception {
        /**
         * Suppose we have multiple dockerfiles that need to updated in a repo and we fail to fork such repo,
         * we should not add those repos to pathToDockerfilesInParentRepo.
         *
         * Note: Sometimes GitHub search API returns the same result twice. This test covers such cases as well.
         */
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);

        GHRepository contentRepo1 = mock(GHRepository.class);
        when(contentRepo1.getFullName()).thenReturn("1");

        GHRepository duplicateContentRepo1 = mock(GHRepository.class);
        // Say we have multiple dockerfiles to be updated in repo "1"
        // Or sometimes GitHub search API returns same result twice.
        when(duplicateContentRepo1.getFullName()).thenReturn("1");

        GHRepository contentRepo2 = mock(GHRepository.class);
        when(contentRepo2.getFullName()).thenReturn("2");

        GHContent content1 = mock(GHContent.class);
        when(content1.getOwner()).thenReturn(contentRepo1);
        when(content1.getPath()).thenReturn("1"); // path to 1st dockerfile in repo "1"

        GHContent content2 = mock(GHContent.class);
        when(content2.getOwner()).thenReturn(duplicateContentRepo1);
        when(content2.getPath()).thenReturn("2"); // path to 2st dockerfile in repo "1"

        GHContent content3 = mock(GHContent.class);
        when(content3.getOwner()).thenReturn(contentRepo2);
        when(content3.getPath()).thenReturn("3");

        PagedSearchIterable<GHContent> contentsWithImage = mock(PagedSearchIterable.class);

        PagedIterator<GHContent> contentsWithImageIterator = mock(PagedIterator.class);
        when(contentsWithImageIterator.hasNext()).thenReturn(true, true, true, false);
        when(contentsWithImageIterator.next()).thenReturn(content1, content2, content3, null);
        when(contentsWithImage.iterator()).thenReturn(contentsWithImageIterator);
        when(dockerfileGitHubUtil.getOrCreateFork(contentRepo1)).thenReturn(null); // repo1 is unforkable
        when(dockerfileGitHubUtil.getOrCreateFork(duplicateContentRepo1)).thenReturn(null); // repo1 is unforkable
        when(dockerfileGitHubUtil.getOrCreateFork(contentRepo2)).thenReturn(mock(GHRepository.class));
        when(dockerfileGitHubUtil.getRepo(contentRepo1.getFullName())).thenReturn(contentRepo1);
        when(dockerfileGitHubUtil.getRepo(duplicateContentRepo1.getFullName())).thenReturn(duplicateContentRepo1);
        when(dockerfileGitHubUtil.getRepo(contentRepo2.getFullName())).thenReturn(contentRepo2);

        Parent parent = new Parent();
        parent.loadDockerfileGithubUtil(dockerfileGitHubUtil);
        Multimap<String, GitHubContentToProcess> repoMap =  parent.forkRepositoriesFoundAndGetPathToDockerfiles(contentsWithImage);

        // Since repo "1" is unforkable, we will only try to update repo "2"
        verify(dockerfileGitHubUtil, times(3)).getOrCreateFork(any());
        assertEquals(repoMap.size(), 1);
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

        when(dockerfileGitHubUtil.getRepo(any())).thenReturn(contentRepo1);

        Parent parent = new Parent();
        parent.loadDockerfileGithubUtil(dockerfileGitHubUtil);
        Multimap<String, GitHubContentToProcess> repoMap = parent.forkRepositoriesFoundAndGetPathToDockerfiles(contentsWithImage);

        verify(dockerfileGitHubUtil, never()).getOrCreateFork(any());
        assertEquals(repoMap.size(), 0);
    }

    @Test
    public void testChangeDockerfiles_pullRequestCreation() throws Exception {
        Map<String, Object> nsMap = ImmutableMap.of(Constants.IMG, "image", Constants.TAG, "tag", Constants.STORE, "store");
        Namespace ns = new Namespace(nsMap);

        GHRepository forkedRepo = mock(GHRepository.class);
        when(forkedRepo.isFork()).thenReturn(true);
        when(forkedRepo.getFullName()).thenReturn("forkedrepo");

        GHRepository parentRepo = mock(GHRepository.class);
        when(parentRepo.getFullName()).thenReturn("repo2");
        when(forkedRepo.getParent()).thenReturn(parentRepo);

        Multimap<String, GitHubContentToProcess> pathToDockerfilesInParentRepo = HashMultimap.create();
        pathToDockerfilesInParentRepo.put("repo1", new GitHubContentToProcess(null, null, "df1"));
        pathToDockerfilesInParentRepo.put("repo2", new GitHubContentToProcess(null, null, "df2"));
        pathToDockerfilesInParentRepo.put("repo3", new GitHubContentToProcess(null, null, "df3"));
        pathToDockerfilesInParentRepo.put("repo4", new GitHubContentToProcess(null, null, "df4"));

        GHContent content = mock(GHContent.class);

        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);

        when(dockerfileGitHubUtil.getPullRequestForImageBranch(eq(forkedRepo), any())).thenReturn(Optional.empty());

        when(dockerfileGitHubUtil.tryRetrievingContent(forkedRepo, "df2",
                "image-tag")).thenReturn(content);

        Parent parent = new Parent();
        parent.loadDockerfileGithubUtil(dockerfileGitHubUtil);
        parent.changeDockerfiles(ns, pathToDockerfilesInParentRepo, new GitHubContentToProcess(forkedRepo, parentRepo, ""), new ArrayList<>());

        Mockito.verify(dockerfileGitHubUtil, times(1))
                .tryRetrievingContent(eq(forkedRepo), eq("df2"), eq("image-tag"));
        Mockito.verify(dockerfileGitHubUtil, times(1))
                .modifyOnGithub(eq(content), eq("image-tag"), eq("image"), eq("tag"), anyString());
        Mockito.verify(dockerfileGitHubUtil, times(1))
                .createPullReq(eq(parentRepo), eq("image-tag"), eq(forkedRepo), anyString());
    }

    @Test
    public void testOnePullRequestForMultipleDockerfilesInSameRepo() throws Exception {
        Map<String, Object> nsMap = ImmutableMap.of(Constants.IMG,
                "image", Constants.TAG,
                "tag", Constants.STORE,
                "store");
        Namespace ns = new Namespace(nsMap);

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

        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        when(dockerfileGitHubUtil.getPullRequestForImageBranch(any(), any())).thenReturn(Optional.empty());
        when(dockerfileGitHubUtil.getRepo(forkedRepo.getFullName())).thenReturn(forkedRepo);
        GHContent forkedRepoContent1 = mock(GHContent.class);
        when(dockerfileGitHubUtil.tryRetrievingContent(eq(forkedRepo),
                eq("df11"), eq("image-tag"))).thenReturn(forkedRepoContent1);
        GHContent forkedRepoContent2 = mock(GHContent.class);
        when(dockerfileGitHubUtil.tryRetrievingContent(eq(forkedRepo),
                eq("df12"), eq("image-tag"))).thenReturn(forkedRepoContent2);

        Parent parent = new Parent();
        parent.loadDockerfileGithubUtil(dockerfileGitHubUtil);
        parent.changeDockerfiles(ns, pathToDockerfilesInParentRepo, new GitHubContentToProcess(forkedRepo, parentRepo, ""), new ArrayList<>());

        // Both Dockerfiles retrieved from the same repo
        Mockito.verify(dockerfileGitHubUtil, times(1)).tryRetrievingContent(eq(forkedRepo),
                eq("df11"), eq("image-tag"));
        Mockito.verify(dockerfileGitHubUtil, times(1)).tryRetrievingContent(eq(forkedRepo),
                eq("df12"), eq("image-tag"));

        // Both Dockerfiles modified
        Mockito.verify(dockerfileGitHubUtil, times(2))
                .modifyOnGithub(any(), eq("image-tag"), eq("image"), eq("tag"), anyString());

        // Only one PR created on the repo with changes to both Dockerfiles.
        Mockito.verify(dockerfileGitHubUtil, times(1)).createPullReq(eq(parentRepo),
                eq("image-tag"), eq(forkedRepo), anyString());
    }

    @Test
    public void testNoPullRequestForMissingDockerfile() throws Exception {
        Map<String, Object> nsMap = ImmutableMap.of(Constants.IMG,
                "image", Constants.TAG,
                "tag", Constants.STORE,
                "store");
        Namespace ns = new Namespace(nsMap);

        Multimap<String, GitHubContentToProcess> pathToDockerfilesInParentRepo = HashMultimap.create();
        pathToDockerfilesInParentRepo.put("repo1", new GitHubContentToProcess(null, null, "missing_df"));

        GHRepository forkedRepo = mock(GHRepository.class);
        when(forkedRepo.isFork()).thenReturn(true);
        when(forkedRepo.getFullName()).thenReturn("forkedrepo");

        GHRepository parentRepo = mock(GHRepository.class);
        when(parentRepo.getFullName()).thenReturn("repo1");
        when(forkedRepo.getParent()).thenReturn(parentRepo);

        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        when(dockerfileGitHubUtil.getPullRequestForImageBranch(any(), any())).thenReturn(Optional.empty());
        // Dockerfile not found anymore when trying to retrieve contents from the forked repo.
        when(dockerfileGitHubUtil.tryRetrievingContent(eq(forkedRepo),
                eq("missing_df"), eq("image-tag"))).thenReturn(null);

        Parent parent = new Parent();
        parent.loadDockerfileGithubUtil(dockerfileGitHubUtil);
        parent.changeDockerfiles(ns, pathToDockerfilesInParentRepo, new GitHubContentToProcess(forkedRepo, parentRepo, ""), new ArrayList<>());

        // trying to retrieve Dockerfile
        Mockito.verify(dockerfileGitHubUtil, times(1)).tryRetrievingContent(eq(forkedRepo),
                eq("missing_df"), eq("image-tag"));

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

        when(dockerfileGitHubUtil.getRepo(any())).thenReturn(parentRepo);

        Parent parent = new Parent();
        parent.loadDockerfileGithubUtil(dockerfileGitHubUtil);
        Multimap<String, GitHubContentToProcess> pathToDockerfiles = parent.forkRepositoriesFoundAndGetPathToDockerfiles(contentsWithImage);
        assertTrue(pathToDockerfiles.isEmpty());
        Mockito.verify(dockerfileGitHubUtil, times(0)).getOrCreateFork(any());
    }
// TODO: COVER THIS SCENARIO
//    @Test
//    public void checkPullRequestNotMadeForArchived() throws Exception {
//        final String repoName = "mock repo";
//        Map<String, Object> nsMap = ImmutableMap.of(Constants.IMG,
//                "image", Constants.TAG,
//                "tag", Constants.STORE,
//                "store");
//        Namespace ns = new Namespace(nsMap);
//
//        GHRepository parentRepo = mock(GHRepository.class);
//        GHRepository forkRepo = mock(GHRepository.class);
//        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
//        GHContent content = mock(GHContent.class);
//        GHMyself myself = mock(GHMyself.class);
//
//        when(parentRepo.isArchived()).thenReturn(true);
//
//        when(parentRepo.getFullName()).thenReturn(repoName);
//        when(forkRepo.getFullName()).thenReturn(repoName);
//        when(content.getOwner()).thenReturn(parentRepo);
//        when(dockerfileGitHubUtil.getRepo(eq(repoName))).thenReturn(forkRepo);
//        when(forkRepo.isFork()).thenReturn(true);
//        when(forkRepo.getParent()).thenReturn(parentRepo);
//        when(dockerfileGitHubUtil.getMyself()).thenReturn(myself);
//        when(dockerfileGitHubUtil.getPullRequestForImageBranch(any(), any())).thenReturn(Optional.empty());
//
//        Multimap<String, ForkWithContentPath> pathToDockerfilesInParentRepo = HashMultimap.create();
//        pathToDockerfilesInParentRepo.put(repoName, null);
//
//        Parent parent = new Parent();
//        parent.loadDockerfileGithubUtil(dockerfileGitHubUtil);
//        PagedSearchIterable<GHContent> results = mock(PagedSearchIterable.class);
//        PagedIterator<GHContent> iterator = mock(PagedIterator.class);
//        when(results.iterator()).thenReturn(iterator);
//        when(iterator.hasNext()).thenReturn(true, false);
//        when(iterator.next()).thenReturn(content);
////        Multimap<String, ForkWithContentPath> forks = parent.forkRepositoriesFoundAndGetPathToDockerfiles(results);
//        parent.changeDockerfiles(ns, pathToDockerfilesInParentRepo, forkRepo, Collections.emptyList());
//
//        Mockito.verify(dockerfileGitHubUtil, Mockito.never())
//                .createPullReq(Mockito.any(), anyString(), Mockito.any(), anyString());
//        //Make sure we at least call the isArchived.
//        Mockito.verify(parentRepo, Mockito.times(1)).isArchived();
////        assertTrue(forks.isEmpty());
//    }
}