/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.subcommands.impl;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.gson.JsonElement;
import com.salesforce.dockerfileimageupdate.utils.Constants;
import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;
import net.sourceforge.argparse4j.inf.Namespace;
import org.kohsuke.github.*;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 * Created by minho.park on 7/19/16.
 */
public class AllTest {
    @Test
    public void testForkRepositoriesFound() throws Exception {
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);

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
        all.loadDockerfileGithubUtil(dockerfileGitHubUtil);
        all.forkRepositoriesFound(ArrayListMultimap.create(), ArrayListMultimap.create(), contentsWithImage, "image");

        Mockito.verify(dockerfileGitHubUtil, times(3)).closeOutdatedPullRequestAndFork(any());
    }

    @Test
    public void testForkRepositoriesFound_unableToforkRepo() throws Exception {
        /**
         * Suppose we have multiple dockerfiles that need to updated in a repo and we fail to fork such repo,
         * we should not add those repos to pathToDockerfilesInParentRepo.
         */
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);

        GHRepository contentRepo1 = mock(GHRepository.class);
        when(contentRepo1.getFullName()).thenReturn("1");

        GHRepository contentRepo2 = mock(GHRepository.class);
        // Say we have multiple dockerfiles to be updated in repo "1"
        when(contentRepo2.getFullName()).thenReturn("1");

        GHRepository contentRepo3 = mock(GHRepository.class);
        when(contentRepo3.getFullName()).thenReturn("2");

        GHContent content1 = mock(GHContent.class);
        when(content1.getOwner()).thenReturn(contentRepo1);
        when(content1.getPath()).thenReturn("1"); // path to 1st dockerfile in repo "1"

        GHContent content2 = mock(GHContent.class);
        when(content2.getOwner()).thenReturn(contentRepo2);
        when(content2.getPath()).thenReturn("2"); // path to 2st dockerfile in repo "1"

        GHContent content3 = mock(GHContent.class);
        when(content3.getOwner()).thenReturn(contentRepo3);
        when(content3.getPath()).thenReturn("3");

        PagedSearchIterable<GHContent> contentsWithImage = mock(PagedSearchIterable.class);

        PagedIterator<GHContent> contentsWithImageIterator = mock(PagedIterator.class);
        when(contentsWithImageIterator.hasNext()).thenReturn(true, true, true, false);
        when(contentsWithImageIterator.next()).thenReturn(content1, content2, content3, null);
        when(contentsWithImage.iterator()).thenReturn(contentsWithImageIterator);
        when(dockerfileGitHubUtil.closeOutdatedPullRequestAndFork(contentRepo1)).thenReturn(null); // repo1 is unforkable
        when(dockerfileGitHubUtil.closeOutdatedPullRequestAndFork(contentRepo2)).thenReturn(null); // repo1 is unforkable
        when(dockerfileGitHubUtil.closeOutdatedPullRequestAndFork(contentRepo3)).thenReturn(new GHRepository());

        All all = new All();
        all.loadDockerfileGithubUtil(dockerfileGitHubUtil);
        Multimap<String, String> pathToDockerfilesInParentRepo = ArrayListMultimap.create();
        Multimap<String, String> imagesFoundInParentRepo = ArrayListMultimap.create();
        all.forkRepositoriesFound(pathToDockerfilesInParentRepo, imagesFoundInParentRepo, contentsWithImage, "image");

        // Since repo "1" is unforkable, we only added repo "2" to pathToDockerfilesInParentRepo
        assertEquals(pathToDockerfilesInParentRepo.size(), 1);
        assertEquals(imagesFoundInParentRepo.size(), 1);
        Mockito.verify(dockerfileGitHubUtil, times(3)).closeOutdatedPullRequestAndFork(any());
    }

    @Test
    public void testChangeDockerfiles_returnIfNotFork() throws Exception {
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        GHRepository currUserRepo = mock(GHRepository.class);
        when(currUserRepo.isFork()).thenReturn(false);

        All all = new All();
        all.loadDockerfileGithubUtil(dockerfileGitHubUtil);
        all.changeDockerfiles(null,
                null,
                null,
                null,
                currUserRepo,
                new ArrayList<>());

        Mockito.verify(dockerfileGitHubUtil, times(0)).getRepo(anyString());
    }

    @Test
    public void testChangeDockerfiles_returnIfNotDesiredParent() throws Exception {

        GHRepository currUserRepo = mock(GHRepository.class);
        when(currUserRepo.isFork()).thenReturn(true);
        when(currUserRepo.getFullName()).thenReturn("forkedrepo5");

        GHRepository forkedRepo = mock(GHRepository.class);
        GHRepository parentRepo = mock(GHRepository.class);
        when(parentRepo.getFullName()).thenReturn("repo5");
        when(forkedRepo.getParent()).thenReturn(parentRepo);
        when(forkedRepo.getDefaultBranch()).thenReturn("branch");

        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        when(dockerfileGitHubUtil.getRepo(currUserRepo.getFullName())).thenReturn(forkedRepo);

        Multimap<String, String> pathToDockerfilesInParentRepo = HashMultimap.create();
        pathToDockerfilesInParentRepo.put("repo1", "df1");
        pathToDockerfilesInParentRepo.put("repo2", "df2");
        pathToDockerfilesInParentRepo.put("repo3", "df3");
        pathToDockerfilesInParentRepo.put("repo4", "df4");

        All all = new All();
        all.loadDockerfileGithubUtil(dockerfileGitHubUtil);
        all.changeDockerfiles(null,
                pathToDockerfilesInParentRepo,
                null,
                null,
                currUserRepo,
                new ArrayList<>());

        Mockito.verify(dockerfileGitHubUtil, times(1)).getRepo(eq(currUserRepo.getFullName()));
        Mockito.verify(dockerfileGitHubUtil, times(0))
                .tryRetrievingContent(eq(forkedRepo), anyString(), anyString());
        Mockito.verify(dockerfileGitHubUtil, times(0))
                .modifyOnGithub(any(), anyString(), anyString(), anyString(), anyString());
        Mockito.verify(dockerfileGitHubUtil, times(0))
                .createPullReq(eq(parentRepo), anyString(), eq(forkedRepo), anyString());
    }

    @Test
    public void testChangeDockerfiles_returnWhenForkedRepoNotFound() throws Exception {

        GHRepository currUserRepo = mock(GHRepository.class);
        when(currUserRepo.isFork()).thenReturn(true);

        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        when(dockerfileGitHubUtil.getRepo(currUserRepo.getFullName())).thenThrow(FileNotFoundException.class);

        All all = new All();
        all.loadDockerfileGithubUtil(dockerfileGitHubUtil);
        all.changeDockerfiles(null, null, null, null,
                currUserRepo, new ArrayList<>());

        Mockito.verify(dockerfileGitHubUtil, times(1)).getRepo(anyString());
        Mockito.verify(dockerfileGitHubUtil, times(0)).tryRetrievingContent(any(),
                anyString(), anyString());
        Mockito.verify(dockerfileGitHubUtil, times(0))
                .modifyOnGithub(any(), anyString(), anyString(), anyString(), anyString());
        Mockito.verify(dockerfileGitHubUtil, times(0)).createPullReq(any(), anyString(), any(), anyString());

    }

    @Test
    public void testChangeDockerfiles_pullRequestCreation() throws Exception {
        Map<String, Object> nsMap = ImmutableMap.of(Constants.IMG,
                "image", Constants.TAG,
                "tag", Constants.STORE,
                "store");
        Namespace ns = new Namespace(nsMap);

        Multimap<String, String> pathToDockerfilesInParentRepo = ArrayListMultimap.create();
        pathToDockerfilesInParentRepo.put("repo1", "df1");
        pathToDockerfilesInParentRepo.put("repo2", "df2");
        pathToDockerfilesInParentRepo.put("repo3", "df3");
        pathToDockerfilesInParentRepo.put("repo4", "df4");

        Multimap<String, String> imagesFoundInParentRepo = ArrayListMultimap.create();
        imagesFoundInParentRepo.put("repo1", "image1");
        imagesFoundInParentRepo.put("repo2", "image2");
        imagesFoundInParentRepo.put("repo3", "image3");
        imagesFoundInParentRepo.put("repo4", "image4");

        Map<String, String> imageToTagMap = ImmutableMap.of(
                "image1", "tag1",
                "image2", "tag2",
                "image3", "tag3",
                "image4", "tag4");

        GHRepository currUserRepo = mock(GHRepository.class);
        when(currUserRepo.isFork()).thenReturn(true);
        when(currUserRepo.getFullName()).thenReturn("forkedrepo");

        GHRepository forkedRepo = mock(GHRepository.class);
        GHRepository parentRepo = mock(GHRepository.class);
        when(parentRepo.getFullName()).thenReturn("repo2");
        when(forkedRepo.getParent()).thenReturn(parentRepo);
        when(forkedRepo.getDefaultBranch()).thenReturn("branch");

        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        when(dockerfileGitHubUtil.getRepo(currUserRepo.getFullName())).thenReturn(forkedRepo);
        GHContent forkedRepoContent = mock(GHContent.class);
        when(dockerfileGitHubUtil.tryRetrievingContent(forkedRepo, "df2", forkedRepo.getDefaultBranch())).thenReturn(forkedRepoContent);

        All all = new All();
        all.loadDockerfileGithubUtil(dockerfileGitHubUtil);
        all.changeDockerfiles(ns, pathToDockerfilesInParentRepo, imagesFoundInParentRepo, imageToTagMap,
                currUserRepo, new ArrayList<>());

        Mockito.verify(dockerfileGitHubUtil, times(1)).getRepo(anyString());
        Mockito.verify(dockerfileGitHubUtil, times(1)).tryRetrievingContent(eq(forkedRepo),
                eq("df2"), eq("branch"));
        Mockito.verify(dockerfileGitHubUtil, times(1))
                .modifyOnGithub(any(), eq("branch"), eq("image2"), eq("tag2"), anyString());
        Mockito.verify(dockerfileGitHubUtil, times(1)).createPullReq(eq(parentRepo),
                eq("branch"), eq(forkedRepo), anyString());
    }

    @Test
    public void testOnePullRequestForMultipleDockerfilesInSameRepo() throws Exception {
        Map<String, Object> nsMap = ImmutableMap.of(Constants.IMG,
                "image", Constants.TAG,
                "tag", Constants.STORE,
                "store");
        Namespace ns = new Namespace(nsMap);

        Multimap<String, String> pathToDockerfilesInParentRepo = ArrayListMultimap.create();
        pathToDockerfilesInParentRepo.put("repo1", "df11");
        pathToDockerfilesInParentRepo.put("repo1", "df12");
        pathToDockerfilesInParentRepo.put("repo3", "df3");
        pathToDockerfilesInParentRepo.put("repo4", "df4");

        Multimap<String, String> imagesFoundInParentRepo = ArrayListMultimap.create();
        imagesFoundInParentRepo.put("repo1", "image11");
        imagesFoundInParentRepo.put("repo1", "image12");
        imagesFoundInParentRepo.put("repo3", "image3");
        imagesFoundInParentRepo.put("repo4", "image4");

        Map<String, String> imageToTagMap = ImmutableMap.of(
                "image11", "tag11",
                "image12", "tag12",
                "image3", "tag3",
                "image4", "tag4");

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

        All all = new All();
        all.loadDockerfileGithubUtil(dockerfileGitHubUtil);
        all.changeDockerfiles(ns, pathToDockerfilesInParentRepo, imagesFoundInParentRepo, imageToTagMap,
                currUserRepo, new ArrayList<>());

        // Get repo only once
        Mockito.verify(dockerfileGitHubUtil, times(1)).getRepo(anyString());

        // Both Dockerfiles retrieved from the same repo
        Mockito.verify(dockerfileGitHubUtil, times(1)).tryRetrievingContent(eq(forkedRepo),
                eq("df11"), eq("branch"));
        Mockito.verify(dockerfileGitHubUtil, times(1)).tryRetrievingContent(eq(forkedRepo),
                eq("df12"), eq("branch"));

        // Both Dockerfiles modified
        Mockito.verify(dockerfileGitHubUtil, times(1))
                .modifyOnGithub(any(), eq("branch"), eq("image11"), eq("tag11"), anyString());
        Mockito.verify(dockerfileGitHubUtil, times(1))
                .modifyOnGithub(any(), eq("branch"), eq("image12"), eq("tag12"), anyString());

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

        Multimap<String, String> pathToDockerfilesInParentRepo = ArrayListMultimap.create();
        pathToDockerfilesInParentRepo.put("repo1", "missing_df");

        Multimap<String, String> imagesFoundInParentRepo = ArrayListMultimap.create();
        imagesFoundInParentRepo.put("repo1", "test");

        Map<String, String> imageToTagMap = ImmutableMap.of(
                "image1", "tag1",
                "image2", "tag2");

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

        All all = new All();
        all.loadDockerfileGithubUtil(dockerfileGitHubUtil);
        all.changeDockerfiles(ns, pathToDockerfilesInParentRepo, imagesFoundInParentRepo, imageToTagMap,
                currUserRepo, new ArrayList<>());

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

        All all = new All();
        all.loadDockerfileGithubUtil(dockerfileGitHubUtil);
        all.forkRepositoriesFound(ArrayListMultimap.create(), ArrayListMultimap.create(), contentsWithImage, "image");
        Mockito.verify(dockerfileGitHubUtil, times(0)).closeOutdatedPullRequestAndFork(any());
    }

    @Test
    public void testParseStoreToImagesMap() throws Exception {
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        when(dockerfileGitHubUtil.getMyself()).thenReturn(mock(GHMyself.class));
        when(dockerfileGitHubUtil.getRepo(anyString())).thenReturn(mock(GHRepository.class));
        GHContent mockContent = mock(GHContent.class);
        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        when(mockContent.read()).thenReturn(classloader.getResourceAsStream("image-store-sample.json"));
        when(dockerfileGitHubUtil.tryRetrievingContent(any(GHRepository.class), anyString(), anyString())).thenReturn(mockContent);

        All all = new All();
        all.loadDockerfileGithubUtil(dockerfileGitHubUtil);
        Set<Map.Entry<String, JsonElement>> imageSet = all.parseStoreToImagesMap("testStore");
        assertNotNull(imageSet);
    }

    @Test
    public void checkPullRequestNotMadeForArchived() throws Exception {
        final String repoName = "mock repo";
        Map<String, Object> nsMap = ImmutableMap.of(Constants.IMG,
                "image", Constants.TAG,
                "tag", Constants.STORE,
                "store");
        Namespace ns = new Namespace(nsMap);

        GHRepository parentRepo = mock(GHRepository.class);
        GHRepository forkRepo = mock(GHRepository.class);
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        GHMyself myself = mock(GHMyself.class);

        when(parentRepo.isArchived()).thenReturn(true);

        when(forkRepo.getFullName()).thenReturn(repoName);
        when(parentRepo.getFullName()).thenReturn(repoName);
        when(dockerfileGitHubUtil.getRepo(eq(repoName))).thenReturn(forkRepo);
        when(forkRepo.isFork()).thenReturn(true);
        when(forkRepo.getParent()).thenReturn(parentRepo);
        when(dockerfileGitHubUtil.getMyself()).thenReturn(myself);

        Multimap<String, String> pathToDockerfilesInParentRepo = HashMultimap.create();
        pathToDockerfilesInParentRepo.put(repoName, null);

        All all = new All();
        all.loadDockerfileGithubUtil(dockerfileGitHubUtil);
        all.changeDockerfiles(ns, pathToDockerfilesInParentRepo, null, null, forkRepo, null);

        Mockito.verify(dockerfileGitHubUtil, Mockito.never())
                .createPullReq(Mockito.any(), anyString(), Mockito.any(), anyString());
        //Make sure we at least check if its archived
        Mockito.verify(parentRepo, Mockito.times(2)).isArchived();
    }
}