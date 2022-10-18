package com.salesforce.dockerfileimageupdate.utils;

import com.google.common.collect.*;
import com.salesforce.dockerfileimageupdate.model.*;
import com.salesforce.dockerfileimageupdate.process.*;
import net.sourceforge.argparse4j.inf.*;
import org.kohsuke.github.*;
import org.mockito.*;
import org.testng.annotations.*;

import java.io.*;
import java.util.*;

import static org.mockito.Mockito.*;
import static org.testng.Assert.assertThrows;

public class PullRequestsTest {
    @Test
    public void testPullRequestsPrepareToCreateSuccessful() throws Exception {
        Map<String, Object> nsMap = ImmutableMap.of(Constants.IMG,
                "image", Constants.TAG,
                "tag", Constants.STORE,
                "store", Constants.SKIP_PR_CREATION,
                false);
        Namespace ns = new Namespace(nsMap);
        PullRequests pullRequests = new PullRequests();
        GitHubPullRequestSender pullRequestSender = mock(GitHubPullRequestSender.class);
        PagedSearchIterable<GHContent> contentsFoundWithImage = mock(PagedSearchIterable.class);
        GitForkBranch gitForkBranch = mock(GitForkBranch.class);
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        RateLimiter rateLimiter = Mockito.spy(new RateLimiter(Constants.DEFAULT_RATE_LIMIT,Constants.DEFAULT_RATE_LIMIT_DURATION
                ,Constants.DEFAULT_TOKEN_ADDING_RATE));
        Multimap<String, GitHubContentToProcess> pathToDockerfilesInParentRepo = ArrayListMultimap.create();
        GitHubContentToProcess gitHubContentToProcess = mock(GitHubContentToProcess.class);
        pathToDockerfilesInParentRepo.put("repo1", gitHubContentToProcess);
        pathToDockerfilesInParentRepo.put("repo2", gitHubContentToProcess);
        when(pullRequestSender.forkRepositoriesFoundAndGetPathToDockerfiles(contentsFoundWithImage, gitForkBranch)).thenReturn(pathToDockerfilesInParentRepo);


        pullRequests.prepareToCreate(ns, pullRequestSender, contentsFoundWithImage,
                gitForkBranch, dockerfileGitHubUtil, rateLimiter);

        verify(dockerfileGitHubUtil, times(2)).changeDockerfiles(eq(ns),
                eq(pathToDockerfilesInParentRepo),
                eq(gitHubContentToProcess), anyList(), eq(gitForkBranch), eq(rateLimiter));
    }

    @Test(expectedExceptions = IOException.class)
    public void testPullRequestsPrepareThrowsException() throws Exception {
        Map<String, Object> nsMap = ImmutableMap.of(Constants.IMG,
                "image", Constants.TAG,
                "tag", Constants.STORE,
                "store", Constants.SKIP_PR_CREATION,
                false);
        Namespace ns = new Namespace(nsMap);
        PullRequests pullRequests = new PullRequests();
        GitHubPullRequestSender pullRequestSender = mock(GitHubPullRequestSender.class);
        PagedSearchIterable<GHContent> contentsFoundWithImage = mock(PagedSearchIterable.class);
        GitForkBranch gitForkBranch = mock(GitForkBranch.class);
        RateLimiter rateLimiter = Mockito.spy(new RateLimiter(Constants.DEFAULT_RATE_LIMIT,Constants.DEFAULT_RATE_LIMIT_DURATION
                ,Constants.DEFAULT_TOKEN_ADDING_RATE));
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        Multimap<String, GitHubContentToProcess> pathToDockerfilesInParentRepo = ArrayListMultimap.create();
        GitHubContentToProcess gitHubContentToProcess = mock(GitHubContentToProcess.class);
        pathToDockerfilesInParentRepo.put("repo1", gitHubContentToProcess);
        GHRepository ghRepository = mock(GHRepository.class);

        when(pullRequestSender.forkRepositoriesFoundAndGetPathToDockerfiles(contentsFoundWithImage, gitForkBranch)).thenReturn(pathToDockerfilesInParentRepo);
        ArgumentCaptor<String> valueCapture = ArgumentCaptor.forClass(String.class);
        when(gitHubContentToProcess.getParent()).thenReturn(ghRepository);
        when(ghRepository.getFullName()).thenReturn("repo");
        doThrow(new IOException("Exception")).when(dockerfileGitHubUtil).changeDockerfiles(
                eq(ns),
                eq(pathToDockerfilesInParentRepo),
                eq(gitHubContentToProcess),
                anyList(),
                eq(gitForkBranch), eq(rateLimiter));

        pullRequests.prepareToCreate(ns, pullRequestSender, contentsFoundWithImage,
                gitForkBranch, dockerfileGitHubUtil, rateLimiter);

        assertThrows(IOException.class, () -> pullRequests.prepareToCreate(ns, pullRequestSender, contentsFoundWithImage,
                gitForkBranch, dockerfileGitHubUtil, rateLimiter));
    }

    @Test
    public void testPullRequestsPrepareToCreateWhenNoDockerfileFound() throws Exception {
        Map<String, Object> nsMap = ImmutableMap.of(Constants.IMG,
                "image", Constants.TAG,
                "tag", Constants.STORE,
                "store", Constants.SKIP_PR_CREATION,
                false);
        Namespace ns = new Namespace(nsMap);
        PullRequests pullRequests = new PullRequests();
        GitHubPullRequestSender pullRequestSender = mock(GitHubPullRequestSender.class);
        PagedSearchIterable<GHContent> contentsFoundWithImage = mock(PagedSearchIterable.class);
        GitForkBranch gitForkBranch = mock(GitForkBranch.class);
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        RateLimiter rateLimiter = Mockito.spy(new RateLimiter(Constants.DEFAULT_RATE_LIMIT,Constants.DEFAULT_RATE_LIMIT_DURATION
                ,Constants.DEFAULT_TOKEN_ADDING_RATE));
        Multimap<String, GitHubContentToProcess> pathToDockerfilesInParentRepo = mock(Multimap.class);
        GitHubContentToProcess gitHubContentToProcess = mock(GitHubContentToProcess.class);
        when(pullRequestSender.forkRepositoriesFoundAndGetPathToDockerfiles(contentsFoundWithImage, gitForkBranch)).thenReturn(pathToDockerfilesInParentRepo);
        Set<String> currUsers = new HashSet<>();
        currUsers.add("repo1");
        when(pathToDockerfilesInParentRepo.keySet()).thenReturn(currUsers);
        pullRequests.prepareToCreate(ns, pullRequestSender, contentsFoundWithImage,
                gitForkBranch, dockerfileGitHubUtil, rateLimiter);

        verify(dockerfileGitHubUtil, times(0)).changeDockerfiles(eq(ns),
                eq(pathToDockerfilesInParentRepo),
                eq(gitHubContentToProcess), anyList(), eq(gitForkBranch), eq(rateLimiter));
    }
}
