/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.utils;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.kohsuke.github.*;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

/**
 * Created by minho.park on 8/2/16.
 */
public class GitHubUtilTest {
    @Test
    public void testGetGithub() throws Exception {
        GitHub github = mock(GitHub.class);
        GitHubUtil gitHubUtil = new GitHubUtil(github);
        assertEquals(gitHubUtil.getGithub(), github);
    }

    @Test
    public void testGetRepo() throws Exception {
        GitHub github = mock(GitHub.class);
        when(github.getRepository(eq("repo"))).thenReturn(new GHRepository());
        GitHubUtil gitHubUtil = new GitHubUtil(github);
        gitHubUtil.getRepo("repo");
        verify(github, times(1)).getRepository(eq("repo"));
    }

    @Test
    public void testGetMyself() throws Exception {
        GitHub github = mock(GitHub.class);
        GHMyself myself = mock(GHMyself.class);
        when(github.getMyself()).thenReturn(myself);
        GitHubUtil gitHubUtil = new GitHubUtil(github);
        assertEquals(gitHubUtil.getMyself(), myself);
    }

    @Test
    public void testStartSearch() throws Exception {
        GitHub github = mock(GitHub.class);
        GitHubUtil gitHubUtil = new GitHubUtil(github);
        gitHubUtil.startSearch();
        verify(github, times(1)).searchContent();
    }

    @Test
    public void testCreateFork() throws Exception {
        GitHub github = mock(GitHub.class);
        GitHubUtil gitHubUtil = new GitHubUtil(github);
        GHRepository repo = mock(GHRepository.class);
        gitHubUtil.createFork(repo);
        verify(repo, times(1)).fork();
    }

    @Test
    public void testCreateForkWithExceptionProceeds() throws Exception {
        GitHub github = mock(GitHub.class);
        GitHubUtil gitHubUtil = new GitHubUtil(github);
        GHRepository repo = mock(GHRepository.class);
        when(repo.fork())
                .thenThrow(new IOException("Some issue with forking occurred and the library throws an IOException"));
        assertNull(gitHubUtil.createFork(repo));
        verify(repo, times(1)).fork();
    }

    @Test
    public void testCreatePullReq_correctCallToPullRequest() throws Exception {
        GitHub github = mock(GitHub.class);
        GitHubUtil gitHubUtil = new GitHubUtil(github);
        GHRepository origRepo = mock(GHRepository.class);
        when(origRepo.getDefaultBranch()).thenReturn("master");
        when(origRepo.createPullRequest(any(), any(), any(), any())).thenReturn(mock(GHPullRequest.class));
        GHRepository forkRepo = mock(GHRepository.class);
        when(forkRepo.getOwnerName()).thenReturn("owner");
        assertEquals(gitHubUtil.createPullReq(origRepo, "branch", forkRepo, "title", "body"), 0);
        verify(origRepo, times(1)).createPullRequest(eq("title"), eq("owner:branch"), eq("master"), eq("body"));
    }

    @Test
    public void testCreatePullReq_errorCase0() throws Exception {
        GitHub github = mock(GitHub.class);
        GitHubUtil gitHubUtil = new GitHubUtil(github);
        GHRepository origRepo = mock(GHRepository.class);
        when(origRepo.getDefaultBranch()).thenReturn("master");
        when(origRepo.createPullRequest(eq("title"), eq("owner:branch"), eq("master"), eq("body")))
                .thenThrow(new IOException("{\"message\":\"Validation Failed\",\"errors\":[{\"resource\":\"PullRequest\",\"code\":\"custom\",\"message\":\"A pull request already exists for someone:somebranch.\"}],\"documentation_url\":\"https://developer.github.com/enterprise/2.6/v3/pulls/#create-a-pull-request\"}"));
        GHRepository forkRepo = mock(GHRepository.class);
        when(forkRepo.getOwnerName()).thenReturn("owner");
        assertEquals(gitHubUtil.createPullReq(origRepo, "branch", forkRepo, "title", "body"), 0);
        verify(origRepo, times(1)).createPullRequest(eq("title"), eq("owner:branch"), eq("master"), eq("body"));
    }

    @Test
    public void testCreatePullReq_errorCase1() throws Exception {
        GitHub github = mock(GitHub.class);
        GitHubUtil gitHubUtil = new GitHubUtil(github);
        GHRepository origRepo = mock(GHRepository.class);
        when(origRepo.getDefaultBranch()).thenReturn("master");
        when(origRepo.createPullRequest(eq("title"), eq("owner:branch"), eq("master"), eq("body")))
                .thenThrow(new IOException("{\"message\":\"Validation Failed\",\"errors\":[{\"resource\":\"PullRequest\",\"code\":\"custom\",\"message\":\"No commits between thisrepo and thatrepo.\"}],\"documentation_url\":\"https://developer.github.com/enterprise/2.6/v3/pulls/#create-a-pull-request\"}"));
        GHRepository forkRepo = mock(GHRepository.class);
        when(forkRepo.getOwnerName()).thenReturn("owner");
        assertEquals(gitHubUtil.createPullReq(origRepo, "branch", forkRepo, "title", "body"), 1);
        verify(origRepo, times(1)).createPullRequest(eq("title"), eq("owner:branch"), eq("master"), eq("body"));
    }

    @Test
    public void testTryRetrievingRepository() throws Exception {
        GitHub github = mock(GitHub.class);
        when(github.getRepository(eq("repo"))).thenReturn(new GHRepository());
        GitHubUtil gitHubUtil = new GitHubUtil(github);
        gitHubUtil.tryRetrievingRepository("repo");
        verify(github, times(1)).getRepository(eq("repo"));
    }

    @Test
    public void testTryRetrievingContent() throws Exception {
        GitHub github = mock(GitHub.class);
        GHRepository repo = mock(GHRepository.class);
        when(repo.getFileContent(eq("path"), eq("branch"))).thenReturn(new GHContent());
        GitHubUtil gitHubUtil = new GitHubUtil(github);
        gitHubUtil.tryRetrievingContent(repo, "path", "branch");
        verify(repo, times(1)).getFileContent(eq("path"), eq("branch"));
    }

    /* There is a timeout because if this part of the code is broken, it might enter 60 seconds of sleep. */
    @Test(timeOut = 1000)
    public void testGetGHRepositories() throws Exception {

        Multimap<String, String> parentToPath = HashMultimap.create();
        parentToPath.put("test1", "test");
        parentToPath.put("test2", "correct");
        parentToPath.put("test3", "test");
        parentToPath.put("test4", "test");

        GHMyself currentUser = mock(GHMyself.class);
        PagedIterable<GHRepository> listOfRepos = mock(PagedIterable.class);

        GHRepository repo1 = mock(GHRepository.class);
        when(repo1.getName()).thenReturn("test1");
        GHRepository repo2 = mock(GHRepository.class);
        when(repo2.getName()).thenReturn("test2");
        GHRepository repo3 = mock(GHRepository.class);
        when(repo3.getName()).thenReturn("test3");
        GHRepository repo4 = mock(GHRepository.class);
        when(repo4.getName()).thenReturn("test4");

        PagedIterator<GHRepository> listOfReposIterator = mock(PagedIterator.class);
        when(listOfReposIterator.hasNext()).thenReturn(true, true, true, true, false);
        /* Uncomment below and check if it times out. */
//        Mockito.when(listOfReposIterator.hasNext()).thenReturn(true, true, true, false, false);
        when(listOfReposIterator.next()).thenReturn(repo1, repo2, repo3, repo4, null);
        when(listOfRepos.iterator()).thenReturn(listOfReposIterator);

        when(currentUser.listRepositories(100, GHMyself.RepositoryListFilter.OWNER)).thenReturn(listOfRepos);

        GitHub github = mock(GitHub.class);
        GitHubUtil gitHubUtil = new GitHubUtil(github);
        List<GHRepository> actualList = gitHubUtil.getGHRepositories(parentToPath, currentUser);
        List<GHRepository> expectedList = new ArrayList<>();
        expectedList.add(repo1);
        expectedList.add(repo2);
        expectedList.add(repo3);
        expectedList.add(repo4);
        assertTrue(actualList.size() == expectedList.size());
        assertTrue(actualList.containsAll(expectedList));
        assertTrue(expectedList.containsAll(actualList));
    }

    @Test
    public void testGetReposForUserAtCurrentInstant() throws Exception {
        GHMyself currentUser = mock(GHMyself.class);
        PagedIterable<GHRepository> listOfRepos = mock(PagedIterable.class);

        GHRepository repo1 = mock(GHRepository.class);
        when(repo1.getName()).thenReturn("test1");
        GHRepository repo2 = mock(GHRepository.class);
        when(repo2.getName()).thenReturn("test2");
        GHRepository repo3 = mock(GHRepository.class);
        when(repo3.getName()).thenReturn("test3");
        GHRepository repo4 = mock(GHRepository.class);
        when(repo4.getName()).thenReturn("test4");

        PagedIterator<GHRepository> listOfReposIterator = mock(PagedIterator.class);
        when(listOfReposIterator.hasNext()).thenReturn(true, true, true, true, false);
        when(listOfReposIterator.next()).thenReturn(repo1, repo2, repo3, repo4, null);
        when(listOfRepos.iterator()).thenReturn(listOfReposIterator);

        when(currentUser.listRepositories(100, GHMyself.RepositoryListFilter.OWNER)).thenReturn(listOfRepos);

        GitHub github = mock(GitHub.class);
        GitHubUtil gitHubUtil = new GitHubUtil(github);
        Map<String, GHRepository> repoByName = gitHubUtil.getReposForUserAtCurrentInstant(currentUser);
        assertTrue(repoByName.size() == 4);
        assertTrue(repoByName.containsKey("test1") && repoByName.get("test1") == repo1);
        assertTrue(repoByName.containsKey("test2") && repoByName.get("test2") == repo2);
        assertTrue(repoByName.containsKey("test3") && repoByName.get("test3") == repo3);
        assertTrue(repoByName.containsKey("test4") && repoByName.get("test4") == repo4);
    }

    @Test
    public void testGetReposForUserAtCurrentInstantWithNullUser() throws Exception {
        GitHub github = mock(GitHub.class);
        GitHubUtil gitHubUtil = new GitHubUtil(github);
        Map<String, GHRepository> repoByName = gitHubUtil.getReposForUserAtCurrentInstant(null);
        assertTrue(repoByName.size() == 0);
    }
}