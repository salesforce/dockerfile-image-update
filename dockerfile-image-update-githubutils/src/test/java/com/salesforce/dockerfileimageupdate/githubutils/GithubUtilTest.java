/*
 * Copyright (c) 2017, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.githubutils;

import com.google.common.collect.ImmutableMap;
import org.kohsuke.github.*;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Map;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.testng.Assert.assertEquals;

/**
 * Created by minho.park on 8/2/16.
 */
public class GithubUtilTest {
    @Test
    public void testGetGithub() throws Exception {
        GitHub github = Mockito.mock(GitHub.class);
        GithubUtil githubUtil = new GithubUtil(github);
        assertEquals(githubUtil.getGithub(), github);
    }

    @Test
    public void testGetRepo() throws Exception {
        GitHub github = Mockito.mock(GitHub.class);
        Mockito.when(github.getRepository(eq("repo"))).thenReturn(new GHRepository());
        GithubUtil githubUtil = new GithubUtil(github);
        githubUtil.getRepo("repo");
        Mockito.verify(github, times(1)).getRepository(eq("repo"));
    }

    @Test
    public void testGetMyself() throws Exception {
        GitHub github = Mockito.mock(GitHub.class);
        GHMyself myself = Mockito.mock(GHMyself.class);
        Mockito.when(github.getMyself()).thenReturn(myself);
        GithubUtil githubUtil = new GithubUtil(github);
        assertEquals(githubUtil.getMyself(), myself);
    }

    @Test
    public void testStartSearch() throws Exception {
        GitHub github = Mockito.mock(GitHub.class);
        GithubUtil githubUtil = new GithubUtil(github);
        githubUtil.startSearch();
        Mockito.verify(github, times(1)).searchContent();
    }

    @Test
    public void testCreateFork() throws Exception {
        GitHub github = Mockito.mock(GitHub.class);
        GithubUtil githubUtil = new GithubUtil(github);
        GHRepository repo = Mockito.mock(GHRepository.class);
        githubUtil.createFork(repo);
        Mockito.verify(repo, times(1)).fork();
    }

    @Test
    public void testCreatePullReq_correctCallToPullRequest() throws Exception {
        GitHub github = Mockito.mock(GitHub.class);
        GithubUtil githubUtil = new GithubUtil(github);
        GHRepository origRepo = Mockito.mock(GHRepository.class);
        Mockito.when(origRepo.getDefaultBranch()).thenReturn("master");
        GHRepository forkRepo = Mockito.mock(GHRepository.class);
        Mockito.when(forkRepo.getOwnerName()).thenReturn("owner");
        assertEquals(githubUtil.createPullReq(origRepo, "branch", forkRepo, "title", "body"), 0);
        Mockito.verify(origRepo, times(1)).createPullRequest(eq("title"), eq("owner:branch"), eq("master"), eq("body"));
    }

    @Test
    public void testCreatePullReq_errorCase0() throws Exception {
        GitHub github = Mockito.mock(GitHub.class);
        GithubUtil githubUtil = new GithubUtil(github);
        GHRepository origRepo = Mockito.mock(GHRepository.class);
        Mockito.when(origRepo.getDefaultBranch()).thenReturn("master");
        Mockito.when(origRepo.createPullRequest(eq("title"), eq("owner:branch"), eq("master"), eq("body")))
                .thenThrow(new IOException("{\"message\":\"Validation Failed\",\"errors\":[{\"resource\":\"PullRequest\",\"code\":\"custom\",\"message\":\"A pull request already exists for someone:somebranch.\"}],\"documentation_url\":\"https://developer.github.com/enterprise/2.6/v3/pulls/#create-a-pull-request\"}"));
        GHRepository forkRepo = Mockito.mock(GHRepository.class);
        Mockito.when(forkRepo.getOwnerName()).thenReturn("owner");
        assertEquals(githubUtil.createPullReq(origRepo, "branch", forkRepo, "title", "body"), 0);
        Mockito.verify(origRepo, times(1)).createPullRequest(eq("title"), eq("owner:branch"), eq("master"), eq("body"));
    }

    @Test
    public void testCreatePullReq_errorCase1() throws Exception {
        GitHub github = Mockito.mock(GitHub.class);
        GithubUtil githubUtil = new GithubUtil(github);
        GHRepository origRepo = Mockito.mock(GHRepository.class);
        Mockito.when(origRepo.getDefaultBranch()).thenReturn("master");
        Mockito.when(origRepo.createPullRequest(eq("title"), eq("owner:branch"), eq("master"), eq("body")))
                .thenThrow(new IOException("{\"message\":\"Validation Failed\",\"errors\":[{\"resource\":\"PullRequest\",\"code\":\"custom\",\"message\":\"No commits between thisrepo and thatrepo.\"}],\"documentation_url\":\"https://developer.github.com/enterprise/2.6/v3/pulls/#create-a-pull-request\"}"));
        GHRepository forkRepo = Mockito.mock(GHRepository.class);
        Mockito.when(forkRepo.getOwnerName()).thenReturn("owner");
        assertEquals(githubUtil.createPullReq(origRepo, "branch", forkRepo, "title", "body"), 1);
        Mockito.verify(origRepo, times(1)).createPullRequest(eq("title"), eq("owner:branch"), eq("master"), eq("body"));
    }

    @Test
    public void testTryRetrievingRepository() throws Exception {
        GitHub github = Mockito.mock(GitHub.class);
        Mockito.when(github.getRepository(eq("repo"))).thenReturn(new GHRepository());
        GithubUtil githubUtil = new GithubUtil(github);
        githubUtil.tryRetrievingRepository("repo");
        Mockito.verify(github, times(1)).getRepository(eq("repo"));
    }

    @Test
    public void testTryRetrievingContent() throws Exception {
        GitHub github = Mockito.mock(GitHub.class);
        GHRepository repo = Mockito.mock(GHRepository.class);
        Mockito.when(repo.getFileContent(eq("path"), eq("branch"))).thenReturn(new GHContent());
        GithubUtil githubUtil = new GithubUtil(github);
        githubUtil.tryRetrievingContent(repo, "path", "branch");
        Mockito.verify(repo, times(1)).getFileContent(eq("path"), eq("branch"));
    }

    /* There is a timeout because if this part of the code is broken, it might enter 60 seconds of sleep. */
    @Test(timeOut = 1000)
    public void testGetGHRepositories() throws Exception{
        Map<String, String> parentToPath = ImmutableMap.of(
                "test1", "test",
                "test2", "test",
                "test3", "test",
                "test4", "test");

        GHMyself currentUser = Mockito.mock(GHMyself.class);
        PagedIterable<GHRepository> listOfRepos = Mockito.mock(PagedIterable.class);

        GHRepository repo1 = Mockito.mock(GHRepository.class);
        Mockito.when(repo1.getName()).thenReturn("test1");
        GHRepository repo2 = Mockito.mock(GHRepository.class);
        Mockito.when(repo2.getName()).thenReturn("test2");
        GHRepository repo3 = Mockito.mock(GHRepository.class);
        Mockito.when(repo3.getName()).thenReturn("test3");
        GHRepository repo4 = Mockito.mock(GHRepository.class);
        Mockito.when(repo4.getName()).thenReturn("test4");

        PagedIterator<GHRepository> listOfReposIterator = Mockito.mock(PagedIterator.class);
        Mockito.when(listOfReposIterator.hasNext()).thenReturn(true, true, true, true, false);
        /* Uncomment below and check if it times out. */
//        Mockito.when(listOfReposIterator.hasNext()).thenReturn(true, true, true, false, false);
        Mockito.when(listOfReposIterator.next()).thenReturn(repo1, repo2, repo3, repo4, null);
        Mockito.when(listOfRepos.iterator()).thenReturn(listOfReposIterator);

        Mockito.when(currentUser.listRepositories(100, GHMyself.RepositoryListFilter.OWNER)).thenReturn(listOfRepos);

        GitHub github = Mockito.mock(GitHub.class);
        GithubUtil githubUtil = new GithubUtil(github);
        PagedIterable<GHRepository> returnList = githubUtil.getGHRepositories(parentToPath, currentUser);
        assertEquals(returnList, listOfRepos);
    }
}