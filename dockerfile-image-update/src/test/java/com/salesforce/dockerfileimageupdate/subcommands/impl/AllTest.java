/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.subcommands.impl;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.gson.JsonElement;
import com.salesforce.dockerfileimageupdate.model.*;
import com.salesforce.dockerfileimageupdate.process.*;
import com.salesforce.dockerfileimageupdate.storage.*;
import com.salesforce.dockerfileimageupdate.subcommands.commonsteps.*;
import com.salesforce.dockerfileimageupdate.utils.Constants;
import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;

import java.util.*;

import net.sourceforge.argparse4j.inf.Namespace;
import org.kohsuke.github.*;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import java.io.FileNotFoundException;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

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
        Common commonSteps = mock(Common.class);
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
        when(all.getCommon()).thenReturn(commonSteps);
        PagedSearchIterable<GHContent> contentsWithImage = mock(PagedSearchIterable.class);
        List<PagedSearchIterable<GHContent>> contentsWithImageList = Collections.singletonList(contentsWithImage);
        Optional<List<PagedSearchIterable<GHContent>>> optionalContentsWithImageList = Optional.of(contentsWithImageList);
        doNothing().when(commonSteps).prepareToCreatePullRequests(ns, pullRequestSender,
                contentsWithImage, gitForkBranch, dockerfileGitHubUtil);
        when(dockerfileGitHubUtil.findFilesWithImage(anyString(), anyMap(),  anyInt())).thenReturn(optionalContentsWithImageList);


        all.execute(ns, dockerfileGitHubUtil);
        Mockito.verify(all, times(1)).getGitForkBranch(anyString(), anyString(), any());
        Mockito.verify(all, times(1)).getPullRequestSender(dockerfileGitHubUtil, ns);
        Mockito.verify(all, times(1)).getCommon();
        Mockito.verify(commonSteps, times(1)).prepareToCreatePullRequests(ns, pullRequestSender,
                contentsWithImage, gitForkBranch, dockerfileGitHubUtil);
    }
}
