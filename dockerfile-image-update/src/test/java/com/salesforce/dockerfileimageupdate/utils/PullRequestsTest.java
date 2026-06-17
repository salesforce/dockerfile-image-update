package com.salesforce.dockerfileimageupdate.utils;

import com.google.common.collect.*;
import com.salesforce.dockerfileimageupdate.model.*;
import com.salesforce.dockerfileimageupdate.process.*;
import net.sourceforge.argparse4j.inf.*;
import org.json.JSONException;
import org.kohsuke.github.*;
import org.mockito.*;
import org.testng.*;
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
               false, Constants.CHECK_FOR_RENOVATE, false);
       Namespace ns = new Namespace(nsMap);
       PullRequests pullRequests = new PullRequests();
       GitHubPullRequestSender pullRequestSender = mock(GitHubPullRequestSender.class);
       PagedSearchIterable<GHContent> contentsFoundWithImage = mock(PagedSearchIterable.class);
       GitForkBranch gitForkBranch = mock(GitForkBranch.class);
       DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
       GithubAppCheck githubAppCheck = mock(GithubAppCheck.class);
       RateLimiter rateLimiter = Mockito.spy(new RateLimiter());
       Multimap<String, GitHubContentToProcess> pathToDockerfilesInParentRepo = ArrayListMultimap.create();
       GitHubContentToProcess gitHubContentToProcess = mock(GitHubContentToProcess.class);
       pathToDockerfilesInParentRepo.put("repo1", gitHubContentToProcess);
       pathToDockerfilesInParentRepo.put("repo2", gitHubContentToProcess);
       when(pullRequestSender.forkRepositoriesFoundAndGetPathToDockerfiles(contentsFoundWithImage, gitForkBranch)).thenReturn(pathToDockerfilesInParentRepo);

       pullRequests.prepareToCreate(ns, pullRequestSender, contentsFoundWithImage,
               gitForkBranch, dockerfileGitHubUtil, rateLimiter);

       verify(dockerfileGitHubUtil, times(2)).changeDockerfiles(eq(ns),
               eq(pathToDockerfilesInParentRepo),
               eq(gitHubContentToProcess), anyList(), eq(gitForkBranch),
               eq(rateLimiter));
   }

   @Test(expectedExceptions = IOException.class)
   public void testPullRequestsPrepareThrowsException() throws Exception {
       Map<String, Object> nsMap = ImmutableMap.of(Constants.IMG,
               "image", Constants.TAG,
               "tag", Constants.STORE,
               "store", Constants.SKIP_PR_CREATION,
               false, Constants.CHECK_FOR_RENOVATE, false);
       Namespace ns = new Namespace(nsMap);
       PullRequests pullRequests = new PullRequests();
       GitHubPullRequestSender pullRequestSender = mock(GitHubPullRequestSender.class);
       PagedSearchIterable<GHContent> contentsFoundWithImage = mock(PagedSearchIterable.class);
       GitForkBranch gitForkBranch = mock(GitForkBranch.class);
       RateLimiter rateLimiter = Mockito.spy(new RateLimiter());
       DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
       GithubAppCheck githubAppCheck = mock(GithubAppCheck.class);
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
               eq(gitForkBranch),
               eq(rateLimiter));

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
               false, Constants.CHECK_FOR_RENOVATE, false);
       Namespace ns = new Namespace(nsMap);
       PullRequests pullRequests = new PullRequests();
       GitHubPullRequestSender pullRequestSender = mock(GitHubPullRequestSender.class);
       PagedSearchIterable<GHContent> contentsFoundWithImage = mock(PagedSearchIterable.class);
       GitForkBranch gitForkBranch = mock(GitForkBranch.class);
       GithubAppCheck githubAppCheck = mock(GithubAppCheck.class);
       RateLimiter rateLimiter = Mockito.spy(new RateLimiter());
       DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
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
               eq(gitHubContentToProcess), anyList(), eq(gitForkBranch),eq(rateLimiter));
   }

    @Test
    public void testPullRequestsPrepareSkipsSendingPRIfRepoOnboardedToRenovate() throws Exception {
        Map<String, Object> nsMap = ImmutableMap.of(Constants.IMG,
            "image", Constants.TAG,
            "tag", Constants.STORE,
            "store", Constants.SKIP_PR_CREATION,
            false, Constants.CHECK_FOR_RENOVATE, true);
        Namespace ns = new Namespace(nsMap);
        PullRequests pullRequests = new PullRequests();
        GitHubPullRequestSender pullRequestSender = mock(GitHubPullRequestSender.class);
        PagedSearchIterable<GHContent> contentsFoundWithImage = mock(PagedSearchIterable.class);
        GitForkBranch gitForkBranch = mock(GitForkBranch.class);
        GithubAppCheck githubAppCheck = mock(GithubAppCheck.class);
        RateLimiter rateLimiter = Mockito.spy(new RateLimiter());
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        Multimap<String, GitHubContentToProcess> pathToDockerfilesInParentRepo = mock(Multimap.class);
        GitHubContentToProcess gitHubContentToProcess = mock(GitHubContentToProcess.class);
        GHRepository ghRepository = mock(GHRepository.class);

        when(pullRequestSender.forkRepositoriesFoundAndGetPathToDockerfiles(contentsFoundWithImage, gitForkBranch)).thenReturn(pathToDockerfilesInParentRepo);
        Set<String> currUsers = new HashSet<>();
        currUsers.add("repo1");
        when(pathToDockerfilesInParentRepo.keySet()).thenReturn(currUsers);
        when(gitHubContentToProcess.getParent()).thenReturn(ghRepository);
        when(ghRepository.getFullName()).thenReturn("repoParent");
        when(githubAppCheck.isGithubAppEnabledOnRepository(anyString())).thenReturn(true);

        pullRequests.prepareToCreate(ns, pullRequestSender, contentsFoundWithImage,
                gitForkBranch, dockerfileGitHubUtil, rateLimiter);

        verify(dockerfileGitHubUtil, times(0)).changeDockerfiles(eq(ns),
                eq(pathToDockerfilesInParentRepo),
                eq(gitHubContentToProcess), anyList(), eq(gitForkBranch),
                eq(rateLimiter));
    }

    @Test
    public void testisRenovateEnabledReturnsFalseIfRenovateConfigFileNotFound() throws IOException {
        PullRequests pullRequests = new PullRequests();
        List<String> filePaths = Collections.singletonList("renovate.json");
        GitHubContentToProcess gitHubContentToProcess = mock(GitHubContentToProcess.class);
        GHRepository ghRepository = mock(GHRepository.class);
        when(gitHubContentToProcess.getParent()).thenReturn(ghRepository);
        when(ghRepository.getFileContent(anyString())).thenThrow(new FileNotFoundException());
        Assert.assertFalse(pullRequests.isRenovateEnabled(filePaths, gitHubContentToProcess));
    }

    @Test
    public void testisRenovateEnabledReturnsFalseIfRenovateConfigFileFoundButIsDisabled() throws IOException {
        PullRequests pullRequests = new PullRequests();
        List<String> filePaths = Collections.singletonList("renovate.json");
        GitHubContentToProcess gitHubContentToProcess = mock(GitHubContentToProcess.class);
        GHRepository ghRepository = mock(GHRepository.class);
        GHContent content = mock(GHContent.class);
        InputStream inputStream = new ByteArrayInputStream("{enabled:false}".getBytes());
        when(gitHubContentToProcess.getParent()).thenReturn(ghRepository);
        when(ghRepository.getFileContent(anyString())).thenReturn(content);
        when(content.read()).thenReturn(inputStream);
        Assert.assertFalse(pullRequests.isRenovateEnabled(filePaths, gitHubContentToProcess));
    }

    @Test
    public void testisRenovateEnabledReturnsTrueIfRenovateConfigFileFoundButEnabledKeyNotFound() throws IOException {
        PullRequests pullRequests = new PullRequests();
        List<String> filePaths = Collections.singletonList("renovate.json");
        GitHubContentToProcess gitHubContentToProcess = mock(GitHubContentToProcess.class);
        GHRepository ghRepository = mock(GHRepository.class);
        GHContent content = mock(GHContent.class);
        InputStream inputStream = new ByteArrayInputStream("{someKey:someValue}".getBytes());
        when(gitHubContentToProcess.getParent()).thenReturn(ghRepository);
        when(ghRepository.getFileContent(anyString())).thenReturn(content);
        when(content.read()).thenReturn(inputStream);
        Assert.assertTrue(pullRequests.isRenovateEnabled(filePaths, gitHubContentToProcess));
    }

    @Test
    public void testisRenovateEnabledReturnsFalseIfRenovateConfigFileFoundAndResourcesThrowAnException() throws IOException {
        PullRequests pullRequests = new PullRequests();
        List<String> filePaths = Collections.singletonList("renovate.json");
        GitHubContentToProcess gitHubContentToProcess = mock(GitHubContentToProcess.class);
        GHRepository ghRepository = mock(GHRepository.class);
        GHContent content = mock(GHContent.class);
        when(gitHubContentToProcess.getParent()).thenReturn(ghRepository);
        when(ghRepository.getFileContent(anyString())).thenReturn(content);
        when(content.read()).thenThrow(new IOException());
        Assert.assertFalse(pullRequests.isRenovateEnabled(filePaths, gitHubContentToProcess));
    }

    @Test
    public void testisRenovateEnabledReturnsFalseIfRenovateConfigFileFoundAndJSONParsingThrowsAnException() throws IOException {
        PullRequests pullRequests = new PullRequests();
        List<String> filePaths = Collections.singletonList("renovate.json");
        GitHubContentToProcess gitHubContentToProcess = mock(GitHubContentToProcess.class);
        GHRepository ghRepository = mock(GHRepository.class);
        GHContent content = mock(GHContent.class);
        when(gitHubContentToProcess.getParent()).thenReturn(ghRepository);
        when(ghRepository.getFileContent(anyString())).thenReturn(content);
        when(content.read()).thenThrow(new JSONException(""));
        Assert.assertFalse(pullRequests.isRenovateEnabled(filePaths, gitHubContentToProcess));
    }

    @Test
    public void testisRenovateEnabledReturnsTrueIfRenovateConfigFileFoundAndEnabledKeySetToTrue() throws IOException {
        PullRequests pullRequests = new PullRequests();
        List<String> filePaths = Collections.singletonList("renovate.json");
        GitHubContentToProcess gitHubContentToProcess = mock(GitHubContentToProcess.class);
        GHRepository ghRepository = mock(GHRepository.class);
        GHContent content = mock(GHContent.class);
        InputStream inputStream = new ByteArrayInputStream("{enabled:true}".getBytes());
        when(gitHubContentToProcess.getParent()).thenReturn(ghRepository);
        when(ghRepository.getFileContent(anyString())).thenReturn(content);
        when(content.read()).thenReturn(inputStream);
        Assert.assertTrue(pullRequests.isRenovateEnabled(filePaths, gitHubContentToProcess));
    }
}
