package com.salesforce.dockerfileimageupdate.process;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.salesforce.dockerfileimageupdate.model.GitHubContentToProcess;
import com.salesforce.dockerfileimageupdate.model.ShouldForkResult;
import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedIterator;
import org.kohsuke.github.PagedSearchIterable;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

public class GitHubPullRequestSenderTest {

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

        ForkableRepoValidator forkableRepoValidator = mock(ForkableRepoValidator.class);
        when(forkableRepoValidator.shouldFork(any(), any(), any())).thenReturn(ShouldForkResult.shouldForkResult());

        GitHubPullRequestSender pullRequestSender = new GitHubPullRequestSender(dockerfileGitHubUtil, forkableRepoValidator, null);
        Multimap<String, GitHubContentToProcess> repoMap =
                pullRequestSender.forkRepositoriesFoundAndGetPathToDockerfiles(contentsWithImage, null);

        verify(dockerfileGitHubUtil, times(3)).getOrCreateFork(any());
        assertEquals(repoMap.size(), 3);
    }

    @Test
    public void testForkRepositoriesFoundWithExcludes() throws Exception {
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);

        GHRepository contentRepo1 = mock(GHRepository.class);
        when(contentRepo1.getFullName()).thenReturn("user//1");
        when(contentRepo1.getName()).thenReturn("1");
        GHRepository contentRepo2 = mock(GHRepository.class);
        when(contentRepo2.getFullName()).thenReturn("user/2");
        when(contentRepo2.getName()).thenReturn("2");
        GHRepository contentRepo3 = mock(GHRepository.class);
        when(contentRepo3.getFullName()).thenReturn("user/3");
        when(contentRepo3.getName()).thenReturn("3");


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

        ForkableRepoValidator forkableRepoValidator = mock(ForkableRepoValidator.class);
        when(forkableRepoValidator.shouldFork(any(), any(), any())).thenReturn(ShouldForkResult.shouldForkResult());

        GitHubPullRequestSender pullRequestSender = new GitHubPullRequestSender(dockerfileGitHubUtil, forkableRepoValidator, "^2$");
        Multimap<String, GitHubContentToProcess> repoMap =
                pullRequestSender.forkRepositoriesFoundAndGetPathToDockerfiles(contentsWithImage, null);

        verify(dockerfileGitHubUtil, times(2)).getOrCreateFork(any());
        assertEquals(repoMap.size(), 2);
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

        ForkableRepoValidator forkableRepoValidator = mock(ForkableRepoValidator.class);
        when(forkableRepoValidator.shouldFork(any(), any(), any())).thenReturn(ShouldForkResult.shouldForkResult());

        GitHubPullRequestSender pullRequestSender = new GitHubPullRequestSender(dockerfileGitHubUtil, forkableRepoValidator, null);
        Multimap<String, GitHubContentToProcess> repoMap =
                pullRequestSender.forkRepositoriesFoundAndGetPathToDockerfiles(contentsWithImage, null);

        // Since repo "1" is unforkable, we will only try to update repo "2"
        verify(dockerfileGitHubUtil, times(3)).getOrCreateFork(any());
        assertEquals(repoMap.size(), 1);
    }

    @Test
    public void testDoNotForkReposWhichDoNotQualify() throws Exception {
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

        ForkableRepoValidator forkableRepoValidator = mock(ForkableRepoValidator.class);
        when(forkableRepoValidator.shouldFork(any(), any(), any())).thenReturn(ShouldForkResult.shouldNotForkResult(""));

        GitHubPullRequestSender pullRequestSender = new GitHubPullRequestSender(dockerfileGitHubUtil, forkableRepoValidator, null);
        Multimap<String, GitHubContentToProcess> repoMap =
                pullRequestSender.forkRepositoriesFoundAndGetPathToDockerfiles(contentsWithImage, null);

        verify(dockerfileGitHubUtil, never()).getOrCreateFork(any());
        assertEquals(repoMap.size(), 0);
    }

    @Test
    public void testGetForkFromExistingRecordToProcess() {
        String reponame = "reponame";
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        GitHubPullRequestSender gitHubPullRequestSender = new GitHubPullRequestSender(dockerfileGitHubUtil, mock(ForkableRepoValidator.class), null);
        GHRepository fork = mock(GHRepository.class);
        GHRepository parent = mock(GHRepository.class);
        Multimap<String, GitHubContentToProcess> processMultimap = HashMultimap.create();
        processMultimap.put(reponame, new GitHubContentToProcess(fork, parent, "path"));
        assertEquals(gitHubPullRequestSender.getForkFromExistingRecordToProcess(processMultimap, reponame), fork);
    }

    @Test
    public void testGetForkNotFoundFromExistingRecords() {
        String reponame = "reponame";
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        GitHubPullRequestSender gitHubPullRequestSender = new GitHubPullRequestSender(dockerfileGitHubUtil, mock(ForkableRepoValidator.class), null);
        Multimap<String, GitHubContentToProcess> processMultimap = HashMultimap.create();
        assertNull(gitHubPullRequestSender.getForkFromExistingRecordToProcess(processMultimap, reponame));
    }
}
