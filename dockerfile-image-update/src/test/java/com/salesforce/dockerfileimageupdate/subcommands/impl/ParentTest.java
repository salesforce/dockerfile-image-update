/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.subcommands.impl;

import com.google.common.collect.ImmutableMap;

import com.salesforce.dockerfileimageupdate.model.*;
import com.salesforce.dockerfileimageupdate.process.*;
import com.salesforce.dockerfileimageupdate.storage.GitHubJsonStore;
import com.salesforce.dockerfileimageupdate.utils.*;
import net.sourceforge.argparse4j.inf.Namespace;
import org.kohsuke.github.*;
import org.testng.annotations.Test;

import java.util.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.*;

/**
 * Created by minho.park on 7/19/16.
 */
public class ParentTest {
    @Test
    public void testPrCreationSkippedWhenSkipPrCreationFlagSetToTrue() throws Exception {
        Map<String, Object> nsMap = ImmutableMap.of(Constants.IMG,
                "image", Constants.TAG,
                "tag", Constants.STORE,
                "store", Constants.SKIP_PR_CREATION,
                true);
        Namespace ns = new Namespace(nsMap);
        Parent parent = spy(new Parent());
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        RateLimiter rateLimiter = spy(new RateLimiter(Constants.DEFAULT_RATE_LIMIT,Constants.DEFAULT_RATE_LIMIT_DURATION
                ,Constants.DEFAULT_TOKEN_ADDING_RATE));
        GitHubPullRequestSender pullRequestSender = mock(GitHubPullRequestSender.class);
        GitForkBranch gitForkBranch = mock(GitForkBranch.class);
        PagedSearchIterable<GHContent> contentsWithImage = mock(PagedSearchIterable.class);
        PullRequests pullRequests = mock(PullRequests.class);
        GitHubJsonStore imageTagStore = mock(GitHubJsonStore.class);

        when(dockerfileGitHubUtil.getGitHubJsonStore("store")).thenReturn(imageTagStore);

        parent.execute(ns, dockerfileGitHubUtil);

        verify(dockerfileGitHubUtil).getGitHubJsonStore("store");
        verify(parent, times(0)).getGitForkBranch(ns);
        verify(parent, times(0)).getPullRequestSender(dockerfileGitHubUtil, ns);
        verify(parent, times(0)).getPullRequests();
        verify(pullRequests, times(0)).prepareToCreate(ns, pullRequestSender,
                contentsWithImage, gitForkBranch, dockerfileGitHubUtil, rateLimiter);
    }

    @Test
    public void testPrCreationSkippedWhenSkipPrCreationFlagSetToTrueForS3ImageStore() throws Exception {
        Map<String, Object> nsMap = ImmutableMap.of(Constants.IMG,
                "image", Constants.TAG,
                "tag", Constants.STORE,
                "s3://store", Constants.SKIP_PR_CREATION,
                true);
        Namespace ns = new Namespace(nsMap);
        Parent parent = spy(new Parent());
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        RateLimiter rateLimiter = spy(new RateLimiter(Constants.DEFAULT_RATE_LIMIT,Constants.DEFAULT_RATE_LIMIT_DURATION
                ,Constants.DEFAULT_TOKEN_ADDING_RATE));
        GitHubPullRequestSender pullRequestSender = mock(GitHubPullRequestSender.class);
        GitForkBranch gitForkBranch = mock(GitForkBranch.class);
        PagedSearchIterable<GHContent> contentsWithImage = mock(PagedSearchIterable.class);
        PullRequests pullRequests = mock(PullRequests.class);

        parent.execute(ns, dockerfileGitHubUtil);

        verify(dockerfileGitHubUtil, times(0)).getGitHubJsonStore("store");
        verify(parent, times(0)).getGitForkBranch(ns);
        verify(parent, times(0)).getPullRequestSender(dockerfileGitHubUtil, ns);
        verify(parent, times(0)).getPullRequests();
        verify(pullRequests, times(0)).prepareToCreate(ns, pullRequestSender,
                contentsWithImage, gitForkBranch, dockerfileGitHubUtil, rateLimiter);
    }

    @Test
    public void testParentCommandSuccessful() throws Exception {
        Map<String, Object> nsMap = ImmutableMap.of(Constants.IMG,
                "image", Constants.TAG,
                "tag", Constants.STORE,
                "store", Constants.SKIP_PR_CREATION,
                false, Constants.USE_RATE_LIMITING,
                false);
        Namespace ns = new Namespace(nsMap);
        Parent parent = spy(new Parent());
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        GitHubPullRequestSender pullRequestSender = mock(GitHubPullRequestSender.class);
        GitForkBranch gitForkBranch = mock(GitForkBranch.class);
        PullRequests pullRequests = mock(PullRequests.class);
        GitHubJsonStore imageTagStore = mock(GitHubJsonStore.class);
        PagedSearchIterable<GHContent> contentsWithImage = mock(PagedSearchIterable.class);
        List<PagedSearchIterable<GHContent>> contentsWithImageList = Collections.singletonList(contentsWithImage);
        Optional<List<PagedSearchIterable<GHContent>>> optionalContentsWithImageList = Optional.of(contentsWithImageList);
        
        when(parent.getPullRequestSender(dockerfileGitHubUtil, ns)).thenReturn(pullRequestSender);
        when(parent.getGitForkBranch(ns)).thenReturn(gitForkBranch);
        when(parent.getPullRequests()).thenReturn(pullRequests);
        doNothing().when(pullRequests).prepareToCreate(ns, pullRequestSender,
                contentsWithImage, gitForkBranch, dockerfileGitHubUtil, null);
        when(dockerfileGitHubUtil.getGHContents(anyString(), anyString(),  anyInt())).thenReturn(optionalContentsWithImageList);
        when(dockerfileGitHubUtil.getGitHubJsonStore("store")).thenReturn(imageTagStore);

        parent.execute(ns, dockerfileGitHubUtil);

        verify(parent).getGitForkBranch(ns);
        verify(parent).getPullRequestSender(dockerfileGitHubUtil, ns);
        verify(parent).getPullRequests();
        verify(pullRequests).prepareToCreate(ns, pullRequestSender,
                contentsWithImage, gitForkBranch, dockerfileGitHubUtil, null);
    }

    @Test
    public void testParentCommandSuccessfulForS3ImageStore() throws Exception {
        Map<String, Object> nsMap = ImmutableMap.of(Constants.IMG,
                "image", Constants.TAG,
                "tag", Constants.STORE,
                "s3://store", Constants.SKIP_PR_CREATION,
                false, Constants.USE_RATE_LIMITING,
                true);
        Namespace ns = new Namespace(nsMap);
        Parent parent = spy(new Parent());
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        GitHubPullRequestSender pullRequestSender = mock(GitHubPullRequestSender.class);
        RateLimiter rateLimiter = spy(new RateLimiter(Constants.DEFAULT_RATE_LIMIT,Constants.DEFAULT_RATE_LIMIT_DURATION
                ,Constants.DEFAULT_TOKEN_ADDING_RATE));
        GitForkBranch gitForkBranch = mock(GitForkBranch.class);
        PullRequests pullRequests = mock(PullRequests.class);
        PagedSearchIterable<GHContent> contentsWithImage = mock(PagedSearchIterable.class);
        List<PagedSearchIterable<GHContent>> contentsWithImageList = Collections.singletonList(contentsWithImage);
        Optional<List<PagedSearchIterable<GHContent>>> optionalContentsWithImageList = Optional.of(contentsWithImageList);


        when(parent.getPullRequestSender(dockerfileGitHubUtil, ns)).thenReturn(pullRequestSender);
        when(parent.getGitForkBranch(ns)).thenReturn(gitForkBranch);
        when(parent.getPullRequests()).thenReturn(pullRequests);
        when(parent.getRateLimiter(ns)).thenReturn(rateLimiter);
        doNothing().when(pullRequests).prepareToCreate(ns, pullRequestSender,
                contentsWithImage, gitForkBranch, dockerfileGitHubUtil, rateLimiter);
        when(dockerfileGitHubUtil.getGHContents(anyString(), anyString(),  anyInt())).thenReturn(optionalContentsWithImageList);


        parent.execute(ns, dockerfileGitHubUtil);

        verify(parent).getGitForkBranch(ns);
        verify(parent).getPullRequestSender(dockerfileGitHubUtil, ns);
        verify(parent).getPullRequests();
        verify(pullRequests).prepareToCreate(ns, pullRequestSender,
                contentsWithImage, gitForkBranch, dockerfileGitHubUtil, rateLimiter);
    }

    @Test
    public void testGetPullRequests(){
        Parent parent = new Parent();
        PullRequests pullRequests = new PullRequests();
        assertEquals(pullRequests.getClass(), parent.getPullRequests().getClass());
    }


    @Test
    public void testGetGitForkBranch() {
        Map<String, Object> nsMap = ImmutableMap.of(Constants.IMG,
                "image", Constants.TAG,
                "tag", Constants.STORE,
                "store", Constants.GIT_BRANCH,
                "branch");
        Namespace ns = new Namespace(nsMap);
        Parent parent = spy(new Parent());
        GitForkBranch gitForkBranch = parent.getGitForkBranch(ns);
        assertEquals(gitForkBranch.getBranchName(), "branch-tag");
        assertEquals(gitForkBranch.getImageName(), "image");
        assertEquals(gitForkBranch.getImageTag(), "tag");
    }

    @Test
    public void testGetPullRequestSender() {
        Map<String, Object> nsMap = ImmutableMap.of(Constants.IMG,
                "image", Constants.TAG,
                "tag", Constants.STORE,
                "store", Constants.GIT_BRANCH,
                "branch");
        Namespace ns = new Namespace(nsMap);
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        Parent parent = spy(new Parent());
        ForkableRepoValidator forkableRepoValidator = mock(ForkableRepoValidator.class);
        GitHubPullRequestSender gitHubPullRequestSender =
                new GitHubPullRequestSender(dockerfileGitHubUtil, forkableRepoValidator, "");

        assertEquals(parent.getPullRequestSender(dockerfileGitHubUtil, ns).getClass(),
                gitHubPullRequestSender.getClass());
    }

    @Test(expectedExceptions = Exception.class)
    public void testParentCommandThrowsException() throws Exception {
        Map<String, Object> nsMap = ImmutableMap.of(Constants.IMG,
                "image", Constants.TAG,
                "tag", Constants.STORE,
                "store", Constants.SKIP_PR_CREATION,
                false);
        Namespace ns = new Namespace(nsMap);
        RateLimiter rateLimiter = spy(new RateLimiter());
        Parent parent = spy(new Parent());
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        GitHubJsonStore gitHubJsonStore = mock(GitHubJsonStore.class);
        GitHubPullRequestSender pullRequestSender = mock(GitHubPullRequestSender.class);
        GitForkBranch gitForkBranch = mock(GitForkBranch.class);
        PullRequests pullRequests = mock(PullRequests.class);
        when(dockerfileGitHubUtil.getGitHubJsonStore(anyString())).thenReturn(gitHubJsonStore);
        when(parent.getPullRequestSender(dockerfileGitHubUtil, ns)).thenReturn(pullRequestSender);
        when(parent.getGitForkBranch(ns)).thenReturn(gitForkBranch);
        when(parent.getPullRequests()).thenReturn(pullRequests);
        PagedSearchIterable<GHContent> contentsWithImage = mock(PagedSearchIterable.class);
        List<PagedSearchIterable<GHContent>> contentsWithImageList = Collections.singletonList(contentsWithImage);
        Optional<List<PagedSearchIterable<GHContent>>> optionalContentsWithImageList = Optional.of(contentsWithImageList);
        doThrow(new InterruptedException("Exception")).when(pullRequests).prepareToCreate(ns, pullRequestSender,
                contentsWithImage, gitForkBranch, dockerfileGitHubUtil, rateLimiter);
        when(dockerfileGitHubUtil.getGHContents(anyString(), anyString(),  anyInt())).thenReturn(optionalContentsWithImageList);

        parent.execute(ns, dockerfileGitHubUtil);

        assertThrows(InterruptedException.class, () -> parent.execute(ns, dockerfileGitHubUtil));
    }
}