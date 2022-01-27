/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.subcommands.impl;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.salesforce.dockerfileimageupdate.model.*;
import com.salesforce.dockerfileimageupdate.process.*;
import com.salesforce.dockerfileimageupdate.storage.*;
import com.salesforce.dockerfileimageupdate.utils.*;

import java.util.*;

import net.sourceforge.argparse4j.inf.Namespace;
import org.kohsuke.github.*;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;

/**
 * Created by minho.park on 7/19/16.
 */
public class AllTest {
    @Test
    public void testAllCommandSuccessful() throws Exception {
        Map<String, Object> nsMap = ImmutableMap.of(Constants.IMG,
                "image", Constants.TAG,
                "tag", Constants.STORE,
                "store");
        Namespace ns = new Namespace(nsMap);
        All all = Mockito.spy(new All());
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        GitHubJsonStore gitHubJsonStore = mock(GitHubJsonStore.class);
        GitHubPullRequestSender pullRequestSender = mock(GitHubPullRequestSender.class);
        GitForkBranch gitForkBranch = mock(GitForkBranch.class);
        PullRequests pullRequests = mock(PullRequests.class);
        Set<Map.Entry<String, JsonElement>> imageToTagStore = mock(Set.class);
        when(dockerfileGitHubUtil.getGitHubJsonStore(anyString())).thenReturn(gitHubJsonStore);
        when(gitHubJsonStore.parseStoreToImagesMap(dockerfileGitHubUtil, "store")).thenReturn(imageToTagStore);

        Map.Entry<String, JsonElement> imageToTag = mock(Map.Entry.class);
        Iterator<Map.Entry<String, JsonElement>> imageToTagStoreIterator = mock(Iterator.class);
        when(imageToTagStoreIterator.next()).thenReturn(imageToTag);
        when(imageToTagStoreIterator.hasNext()).thenReturn(true, false);
        when(imageToTagStore.iterator()).thenReturn(imageToTagStoreIterator);
        JsonElement jsonElement = mock(JsonElement.class);
        when(imageToTag.getKey()).thenReturn("image1");
        when(imageToTag.getValue()).thenReturn(jsonElement);
        when(jsonElement.getAsString()).thenReturn("tag1");
        when(all.getPullRequestSender(dockerfileGitHubUtil, ns)).thenReturn(pullRequestSender);
        when(all.getGitForkBranch("image1", "tag1", ns)).thenReturn(gitForkBranch);
        when(all.getPullRequests()).thenReturn(pullRequests);
        PagedSearchIterable<GHContent> contentsWithImage = mock(PagedSearchIterable.class);
        List<PagedSearchIterable<GHContent>> contentsWithImageList = Collections.singletonList(contentsWithImage);
        Optional<List<PagedSearchIterable<GHContent>>> optionalContentsWithImageList = Optional.of(contentsWithImageList);
        doNothing().when(pullRequests).prepareToCreate(ns, pullRequestSender,
                contentsWithImage, gitForkBranch, dockerfileGitHubUtil);
        when(dockerfileGitHubUtil.findFilesWithImage(anyString(), anyMap(),  anyInt())).thenReturn(optionalContentsWithImageList);


        all.execute(ns, dockerfileGitHubUtil);
        verify(all, times(1)).getGitForkBranch(anyString(), anyString(), any());
        verify(all, times(1)).getPullRequestSender(dockerfileGitHubUtil, ns);
        verify(all, times(1)).getPullRequests();
        verify(pullRequests, times(1)).prepareToCreate(ns, pullRequestSender,
                contentsWithImage, gitForkBranch, dockerfileGitHubUtil);
    }

    @Test
    public void testGetCommon(){
        All all = new All();
        PullRequests pullRequests = new PullRequests();
        assertEquals(pullRequests.getClass(), all.getPullRequests().getClass());
    }

    @Test
    public void testGetGitForkBranch() {
        Map<String, Object> nsMap = ImmutableMap.of(Constants.IMG,
                "image", Constants.TAG,
                "tag", Constants.STORE,
                "store", Constants.GIT_BRANCH,
                "branch");
        Namespace ns = new Namespace(nsMap);
        All all = Mockito.spy(new All());
        GitForkBranch gitForkBranch = all.getGitForkBranch("image", "tag", ns);
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
        All all = Mockito.spy(new All());
        ForkableRepoValidator forkableRepoValidator = mock(ForkableRepoValidator.class);
        GitHubPullRequestSender gitHubPullRequestSender =
                new GitHubPullRequestSender(dockerfileGitHubUtil, forkableRepoValidator, "");

        assertEquals(all.getPullRequestSender(dockerfileGitHubUtil, ns).getClass(),
                gitHubPullRequestSender.getClass());
    }
}
