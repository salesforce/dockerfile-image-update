package com.salesforce.dockerfileimageupdate.repository;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.kohsuke.github.GHRepository;
import org.mockito.Mock;
import org.testng.annotations.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class GitHubTest {
    String repoName = "some-repo-name";

    @Mock
    GHRepository gitHubRepository;


    @Test
    public void testShouldNotProcessBecauseNullParent() {
        assertTrue(GitHub.shouldNotProcessDockerfilesInRepo(null, null));
    }

    @Test
    public void testShouldNotProcessBecauseDidNotFindDockerfiles() {
        gitHubRepository = mock(GHRepository.class);
        when(gitHubRepository.getFullName()).thenReturn(repoName);
        Multimap<String, String> multimap = HashMultimap.create();
        assertTrue(GitHub.shouldNotProcessDockerfilesInRepo(multimap, gitHubRepository));
    }

    @Test
    public void testShouldNotProcessBecauseIsArchived() {
        gitHubRepository = mock(GHRepository.class);
        when(gitHubRepository.getFullName()).thenReturn(repoName);
        when(gitHubRepository.isArchived()).thenReturn(true);
        Multimap<String, String> multimap = HashMultimap.create();
        multimap.put(repoName, null);
        assertTrue(GitHub.shouldNotProcessDockerfilesInRepo(multimap, gitHubRepository));
    }

    @Test
    public void testShouldProcess() {
        gitHubRepository = mock(GHRepository.class);
        when(gitHubRepository.getFullName()).thenReturn(repoName);
        Multimap<String, String> multimap = HashMultimap.create();
        multimap.put(repoName, null);
        assertFalse(GitHub.shouldNotProcessDockerfilesInRepo(multimap, gitHubRepository));
    }
}