package com.salesforce.dockerfileimageupdate.repository;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.kohsuke.github.GHBranch;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTree;
import org.mockito.Mock;
import org.testng.annotations.Test;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class GitHubTest {
    String repoName = "some-repo-name";

    @Mock
    GHRepository gitHubRepository;
    @Mock
    GHRepository fork;


    @Test
    public void testShouldNotProcessBecauseNullParent() {
        assertTrue(GitHub.shouldNotProcessDockerfilesInRepo(null, null));
    }

    private void setupGitHubRepo() {
        gitHubRepository = mock(GHRepository.class);
        when(gitHubRepository.getFullName()).thenReturn(repoName);
    }

    @Test
    public void testShouldNotProcessBecauseDidNotFindDockerfiles() {
        setupGitHubRepo();
        Multimap<String, String> multimap = HashMultimap.create();
        assertTrue(GitHub.shouldNotProcessDockerfilesInRepo(multimap, gitHubRepository));
    }

    @Test
    public void testShouldNotProcessBecauseIsArchived() {
        setupGitHubRepo();
        when(gitHubRepository.isArchived()).thenReturn(true);
        Multimap<String, String> multimap = HashMultimap.create();
        multimap.put(repoName, null);
        assertTrue(GitHub.shouldNotProcessDockerfilesInRepo(multimap, gitHubRepository));
    }

    @Test
    public void testShouldProcess() {
        setupGitHubRepo();
        Multimap<String, String> multimap = HashMultimap.create();
        multimap.put(repoName, null);
        assertFalse(GitHub.shouldNotProcessDockerfilesInRepo(multimap, gitHubRepository));
    }

    @Test
    public void testIsForkStaleReturnsTrue() throws IOException {
        setupGitHubRepo();
        GHBranch mainBranch = mock(GHBranch.class);
        when(gitHubRepository.getDefaultBranch()).thenReturn("main");
        when(gitHubRepository.getBranch(anyString())).thenReturn(mainBranch);
        fork = mock(GHRepository.class);
        when(fork.getTree(any())).thenThrow(new GHFileNotFoundException("oh noes"));
        assertTrue(GitHub.isForkStale(gitHubRepository, fork));
    }

    @Test
    public void testIsForkStaleReturnsFalse() throws IOException {
        setupGitHubRepo();
        String mainBranchName = "main";
        String jenny = "8675309";
        when(gitHubRepository.getDefaultBranch()).thenReturn(mainBranchName);
        GHBranch mainBranch = mock(GHBranch.class);
        when(mainBranch.getSHA1()).thenReturn(jenny);
        when(gitHubRepository.getBranch(mainBranchName)).thenReturn(mainBranch);
        fork = mock(GHRepository.class);
        when(fork.getTree(jenny)).thenReturn(mock(GHTree.class));
        assertFalse(GitHub.isForkStale(gitHubRepository, fork));
    }
}