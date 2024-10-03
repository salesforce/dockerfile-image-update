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
import org.mockito.InjectMocks;
import static org.testng.Assert.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Scanner;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.StatusLine;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.MaxRetriesExceeded;
import java.time.Duration;
import java.util.function.Supplier;
import java.io.UncheckedIOException;


@ExtendWith(MockitoExtension.class)
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
    public void testIsGithubAppEnabledOnRepositoryWithRenovateApi_Success() throws IOException {
        String fullRepoName = "org/repo";
        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
        CloseableHttpResponse closeableHttpResponse = mock(CloseableHttpResponse.class);
        GithubAppCheck githubAppCheck = spy(new GithubAppCheck(mock(Namespace.class)));
        StatusLine statusline = mock(StatusLine.class);
        HttpGet httpGet = mock(HttpGet.class);

        when(httpClient.execute(any(HttpGet.class))).thenReturn(closeableHttpResponse);
        when(closeableHttpResponse.getStatusLine()).thenReturn(statusline);
        when(statusline.getStatusCode()).thenReturn(200);

        boolean result = githubAppCheck.isGithubAppEnabledOnRepositoryWithRenovateApi(fullRepoName, httpClient);
        
        verify(httpClient).execute(any());
        assertTrue(result);
    }

    @Test
    public void testIsGithubAppEnabledOnRepositoryWithRenovateApi_Failure() throws IOException {
        String fullRepoName = "org/repo";
        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
        CloseableHttpResponse closeableHttpResponse = mock(CloseableHttpResponse.class);
        GithubAppCheck githubAppCheck = spy(new GithubAppCheck(mock(Namespace.class)));
        StatusLine statusline = mock(StatusLine.class);
        HttpGet httpGet = mock(HttpGet.class);

        when(httpClient.execute(any(HttpGet.class))).thenReturn(closeableHttpResponse);
        when(closeableHttpResponse.getStatusLine()).thenReturn(statusline);
        when(statusline.getStatusCode()).thenReturn(401);

        boolean result = githubAppCheck.isGithubAppEnabledOnRepositoryWithRenovateApi(fullRepoName, httpClient);
        
        verify(httpClient).execute(any(HttpGet.class));
        assertFalse(result);
    }

    @Test
    public void testIsGithubAppEnabledOnRepositoryWithRenovateApi_Throw() throws IOException {
        String fullRepoName = "org/repo";
        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
        CloseableHttpResponse closeableHttpResponse = mock(CloseableHttpResponse.class);
        GithubAppCheck githubAppCheck = spy(new GithubAppCheck(mock(Namespace.class)));
        StatusLine statusline = mock(StatusLine.class);
        HttpGet httpGet = mock(HttpGet.class);

        when(httpClient.execute(any(HttpGet.class))).thenReturn(closeableHttpResponse);
        when(closeableHttpResponse.getStatusLine()).thenReturn(statusline);
        when(statusline.getStatusCode()).thenReturn(500);

        assertThrows(UncheckedIOException.class, () -> githubAppCheck.isGithubAppEnabledOnRepositoryWithRenovateApi(fullRepoName, httpClient));
    }

    @Test
    public void testIsGithubAppEnabledOnRepositoryWithGitApi_Success() throws IOException {
        String fullRepoName = "org/repo";
        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
        CloseableHttpResponse closeableHttpResponse = mock(CloseableHttpResponse.class);
        GithubAppCheck githubAppCheck = spy(new GithubAppCheck(mock(Namespace.class)));
        StatusLine statusline = mock(StatusLine.class);
        HttpGet httpGet = mock(HttpGet.class);

        doNothing().when(githubAppCheck).refreshJwtIfNeeded(any(), any());
        when(httpClient.execute(any(HttpGet.class))).thenReturn(closeableHttpResponse);
        when(closeableHttpResponse.getStatusLine()).thenReturn(statusline);
        when(statusline.getStatusCode()).thenReturn(200);

        boolean result = githubAppCheck.isGithubAppEnabledOnRepositoryWithGitApi(fullRepoName, httpClient);
        
        verify(httpClient).execute(any(HttpGet.class));
        assertTrue(result);
    }

    @Test
    public void testIsGithubAppEnabledOnRepositoryWithGitApi_Failure() throws IOException {
        String fullRepoName = "org/repo";
        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
        CloseableHttpResponse closeableHttpResponse = mock(CloseableHttpResponse.class);
        GithubAppCheck githubAppCheck = spy(new GithubAppCheck(mock(Namespace.class)));
        StatusLine statusline = mock(StatusLine.class);
        HttpGet httpGet = mock(HttpGet.class);

        doNothing().when(githubAppCheck).refreshJwtIfNeeded(any(), any());
        when(httpClient.execute(any(HttpGet.class))).thenReturn(closeableHttpResponse);
        when(closeableHttpResponse.getStatusLine()).thenReturn(statusline);
        when(statusline.getStatusCode()).thenReturn(404);

        boolean result = githubAppCheck.isGithubAppEnabledOnRepositoryWithGitApi(fullRepoName, httpClient);
        
        verify(httpClient).execute(any(HttpGet.class));
        assertFalse(result);
    }

    @Test
    public void testIsGithubAppEnabledOnRepositoryWithGitApi_Throw() throws IOException {
        String fullRepoName = "org/repo";
        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
        CloseableHttpResponse closeableHttpResponse = mock(CloseableHttpResponse.class);
        GithubAppCheck githubAppCheck = spy(new GithubAppCheck(mock(Namespace.class)));
        StatusLine statusline = mock(StatusLine.class);
        HttpGet httpGet = mock(HttpGet.class);

        doNothing().when(githubAppCheck).refreshJwtIfNeeded(any(), any());
        when(httpClient.execute(any(HttpGet.class))).thenReturn(closeableHttpResponse);
        when(closeableHttpResponse.getStatusLine()).thenReturn(statusline);
        when(statusline.getStatusCode()).thenReturn(500);

        assertThrows(UncheckedIOException.class, () -> githubAppCheck.isGithubAppEnabledOnRepositoryWithGitApi(fullRepoName, httpClient));
    }

    @Test
    public void testIsGithubAppEnabledOnRepositoryWithRetry() throws IOException {
        String fullRepoName = "org/repo";
        CloseableHttpClient httpClient = mock(CloseableHttpClient.class);
        CloseableHttpResponse closeableHttpResponse = mock(CloseableHttpResponse.class);
        GithubAppCheck githubAppCheck = spy(new GithubAppCheck(mock(Namespace.class)));
        StatusLine statusline = mock(StatusLine.class);
        HttpGet httpGet = mock(HttpGet.class);
        Supplier supplier = mock(Supplier.class);

        doNothing().when(githubAppCheck).refreshJwtIfNeeded(any(), any());
        when(httpClient.execute(any(HttpGet.class))).thenReturn(closeableHttpResponse);
        when(closeableHttpResponse.getStatusLine()).thenReturn(statusline);
        when(statusline.getStatusCode()).thenReturn(500);
        doThrow(new UncheckedIOException(new IOException())).when(githubAppCheck).isGithubAppEnabledOnRepositoryWithRenovateApi(fullRepoName);
        doThrow(new UncheckedIOException(new IOException())).when(githubAppCheck).isGithubAppEnabledOnRepositoryWithGitApi(fullRepoName);
        doThrow(new MaxRetriesExceeded("test")).when(githubAppCheck).isGithubAppEnabledOnRepositoryWithRetry(fullRepoName, supplier);

        assertThrows(UncheckedIOException.class, () -> githubAppCheck.isGithubAppEnabledOnRepository(fullRepoName));
        verify(githubAppCheck, times(2)).isGithubAppEnabledOnRepositoryWithRenovateApi(fullRepoName); 
        verify(githubAppCheck, times(2)).isGithubAppEnabledOnRepositoryWithGitApi(fullRepoName); 
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
