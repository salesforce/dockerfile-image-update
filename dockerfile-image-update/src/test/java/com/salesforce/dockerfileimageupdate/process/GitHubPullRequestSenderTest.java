package com.salesforce.dockerfileimageupdate.process;

import com.google.common.collect.Multimap;
import com.salesforce.dockerfileimageupdate.model.GitHubContentToProcess;
import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedIterator;
import org.kohsuke.github.PagedSearchIterable;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

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

        GitHubPullRequestSender pullRequestSender = new GitHubPullRequestSender(dockerfileGitHubUtil);
        Multimap<String, GitHubContentToProcess> repoMap =  pullRequestSender.forkRepositoriesFoundAndGetPathToDockerfiles(contentsWithImage);

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

        GitHubPullRequestSender pullRequestSender = new GitHubPullRequestSender(dockerfileGitHubUtil);
        Multimap<String, GitHubContentToProcess> repoMap =  pullRequestSender.forkRepositoriesFoundAndGetPathToDockerfiles(contentsWithImage);

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

        GitHubPullRequestSender pullRequestSender = new GitHubPullRequestSender(dockerfileGitHubUtil);
        Multimap<String, GitHubContentToProcess> repoMap = pullRequestSender.forkRepositoriesFoundAndGetPathToDockerfiles(contentsWithImage);

        verify(dockerfileGitHubUtil, never()).getOrCreateFork(any());
        assertEquals(repoMap.size(), 0);
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

        GitHubPullRequestSender pullRequestSender = new GitHubPullRequestSender(dockerfileGitHubUtil);
        Multimap<String, GitHubContentToProcess> pathToDockerfiles = pullRequestSender.forkRepositoriesFoundAndGetPathToDockerfiles(contentsWithImage);
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