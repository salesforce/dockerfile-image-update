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
import org.mockito.Mockito;
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
        Parent parent = Mockito.spy(new Parent());
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        GitHubJsonStore gitHubJsonStore = mock(GitHubJsonStore.class);
        GitHubPullRequestSender pullRequestSender = mock(GitHubPullRequestSender.class);
        GitForkBranch gitForkBranch = mock(GitForkBranch.class);
        PagedSearchIterable<GHContent> contentsWithImage = mock(PagedSearchIterable.class);
        PullRequests pullRequests = mock(PullRequests.class);
        when(dockerfileGitHubUtil.getGitHubJsonStore(anyString())).thenReturn(gitHubJsonStore);

        parent.execute(ns, dockerfileGitHubUtil);

        Mockito.verify(parent, times(0)).getGitForkBranch(ns);
        Mockito.verify(parent, times(0)).getPullRequestSender(dockerfileGitHubUtil, ns);
        Mockito.verify(parent, times(0)).getPullRequests();
        Mockito.verify(pullRequests, times(0)).prepareToCreate(ns, pullRequestSender,
                contentsWithImage, gitForkBranch, dockerfileGitHubUtil);
    }

    @Test
    public void testParentCommandSuccessful() throws Exception {
        Map<String, Object> nsMap = ImmutableMap.of(Constants.IMG,
                "image", Constants.TAG,
                "tag", Constants.STORE,
                "store", Constants.SKIP_PR_CREATION,
                false);
        Namespace ns = new Namespace(nsMap);
        Parent parent = Mockito.spy(new Parent());
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
        doNothing().when(pullRequests).prepareToCreate(ns, pullRequestSender,
                contentsWithImage, gitForkBranch, dockerfileGitHubUtil);
        when(dockerfileGitHubUtil.getGHContents(anyString(), anyString(),  anyInt())).thenReturn(optionalContentsWithImageList);

        parent.execute(ns, dockerfileGitHubUtil);
        Mockito.verify(parent, times(1)).getGitForkBranch(ns);
        Mockito.verify(parent, times(1)).getPullRequestSender(dockerfileGitHubUtil, ns);
        Mockito.verify(parent, times(1)).getPullRequests();
        Mockito.verify(pullRequests, times(1)).prepareToCreate(ns, pullRequestSender,
                contentsWithImage, gitForkBranch, dockerfileGitHubUtil);
    }

    @Test
    public void testGetCommon(){
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
        Parent parent = Mockito.spy(new Parent());
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
        Parent parent = Mockito.spy(new Parent());
        ForkableRepoValidator forkableRepoValidator = mock(ForkableRepoValidator.class);
        GitHubPullRequestSender gitHubPullRequestSender =
                new GitHubPullRequestSender(dockerfileGitHubUtil, forkableRepoValidator, "");

        assertEquals(parent.getPullRequestSender(dockerfileGitHubUtil, ns).getClass(),
                gitHubPullRequestSender.getClass());
    }
}