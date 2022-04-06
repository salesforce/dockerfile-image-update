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
import com.salesforce.dockerfileimageupdate.storage.*;
import com.salesforce.dockerfileimageupdate.utils.*;

import java.io.IOException;
import java.util.*;

import net.sourceforge.argparse4j.inf.Namespace;
import org.kohsuke.github.*;
import org.testng.annotations.Test;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;

/**
 * Created by avimanyum on 01/31/22.
 */
public class AllTest {

    @Test
    public void testAllCommandSuccessful() throws Exception {
        Map<String, Object> nsMap = ImmutableMap.of(Constants.IMG,
                "image", Constants.TAG,
                "tag", Constants.STORE,
                "store");
        Namespace ns = new Namespace(nsMap);
        All all = spy(new All());
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        GitHubPullRequestSender pullRequestSender = mock(GitHubPullRequestSender.class);
        GitForkBranch gitForkBranch = mock(GitForkBranch.class);
        PullRequests pullRequests = mock(PullRequests.class);
        GitHubJsonStore imageTagStore = mock(GitHubJsonStore.class);
        ImageTagStoreContent imageTagStoreContent = mock(ImageTagStoreContent.class);
        PagedSearchIterable<GHContent> contentsWithImage = mock(PagedSearchIterable.class);
        Iterator<ImageTagStoreContent> imageTagStoreContentIterator = mock(Iterator.class);

        List<ImageTagStoreContent> storeContents = mock(LinkedList.class);
        List<PagedSearchIterable<GHContent>> contentsWithImageList = Collections.singletonList(contentsWithImage);
        Optional<List<PagedSearchIterable<GHContent>>> optionalContentsWithImageList = Optional.of(contentsWithImageList);

        when(dockerfileGitHubUtil.getGitHubJsonStore("store")).thenReturn(imageTagStore);
        when(imageTagStoreContentIterator.next()).thenReturn(imageTagStoreContent);
        when(imageTagStoreContentIterator.hasNext()).thenReturn(true, false);
        when(imageTagStore.getStoreContent(dockerfileGitHubUtil, "store")).thenReturn(storeContents);
        when(storeContents.iterator()).thenReturn(imageTagStoreContentIterator);
        when(imageTagStoreContent.getImageName()).thenReturn("image1");
        when(imageTagStoreContent.getTag()).thenReturn("tag1");
        when(all.getPullRequestSender(dockerfileGitHubUtil, ns)).thenReturn(pullRequestSender);
        when(all.getGitForkBranch("image1", "tag1", ns)).thenReturn(gitForkBranch);
        when(all.getPullRequests()).thenReturn(pullRequests);
        doNothing().when(pullRequests).prepareToCreate(ns, pullRequestSender,
                contentsWithImage, gitForkBranch, dockerfileGitHubUtil);
        when(dockerfileGitHubUtil.findFilesWithImage(anyString(), anyMap(),  anyInt())).thenReturn(optionalContentsWithImageList);

        all.execute(ns, dockerfileGitHubUtil);
        verify(all, times(1)).getGitForkBranch(anyString(), anyString(), any());
        verify(all, times(1)).getPullRequestSender(dockerfileGitHubUtil, ns);
        verify(all, times(1)).getPullRequests();
        verify(pullRequests, times(1)).prepareToCreate(ns, pullRequestSender,
                contentsWithImage, gitForkBranch, dockerfileGitHubUtil);
        verify(all, times(0)).processErrorMessages(anyString(), anyString(), any());
        verify(all, times(1)).printSummary(anyList(), any());

    }

    @Test
    public void testAllCommandSkipsSendingPRsIfSearchReturnsEmpty() throws Exception {
        Map<String, Object> nsMap = ImmutableMap.of(Constants.IMG,
                "image", Constants.TAG,
                "tag", Constants.STORE,
                "store");
        Namespace ns = new Namespace(nsMap);
        All all = spy(new All());
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        GitHubPullRequestSender pullRequestSender = mock(GitHubPullRequestSender.class);
        GitForkBranch gitForkBranch = mock(GitForkBranch.class);
        PullRequests pullRequests = mock(PullRequests.class);
        GitHubJsonStore imageTagStore = mock(GitHubJsonStore.class);
        ImageTagStoreContent imageTagStoreContent = mock(ImageTagStoreContent.class);
        ImageStoreUtil imageStoreUtil = mock(ImageStoreUtil.class);

        List<ImageTagStoreContent> storeContents = mock(LinkedList.class);

        when(dockerfileGitHubUtil.getGitHubJsonStore("store")).thenReturn(imageTagStore);
        when(imageTagStore.getStoreContent(dockerfileGitHubUtil, "store")).thenReturn(storeContents);


        Iterator<ImageTagStoreContent> imageTagStoreContentIterator = mock(Iterator.class);
        when(imageTagStoreContentIterator.next()).thenReturn(imageTagStoreContent);
        when(imageTagStoreContentIterator.hasNext()).thenReturn(true, false);
        when(storeContents.iterator()).thenReturn(imageTagStoreContentIterator);
        when(imageTagStoreContent.getImageName()).thenReturn("image1");
        when(imageTagStoreContent.getTag()).thenReturn("tag1");
        when(all.getPullRequestSender(dockerfileGitHubUtil, ns)).thenReturn(pullRequestSender);
        when(all.getGitForkBranch("image1", "tag1", ns)).thenReturn(gitForkBranch);
        when(all.getPullRequests()).thenReturn(pullRequests);
        PagedSearchIterable<GHContent> contentsWithImage = mock(PagedSearchIterable.class);
        Optional<List<PagedSearchIterable<GHContent>>> optionalContentsWithImageList = Optional.empty();
        doNothing().when(pullRequests).prepareToCreate(ns, pullRequestSender,
                contentsWithImage, gitForkBranch, dockerfileGitHubUtil);
        when(dockerfileGitHubUtil.findFilesWithImage(anyString(), anyMap(),  anyInt())).thenReturn(optionalContentsWithImageList);


        all.execute(ns, dockerfileGitHubUtil);
        verify(all, times(1)).getGitForkBranch(anyString(), anyString(), any());
        verify(all, times(1)).getPullRequestSender(dockerfileGitHubUtil, ns);
        verify(all, times(1)).getPullRequests();
        verify(pullRequests, times(0)).prepareToCreate(ns, pullRequestSender,
                contentsWithImage, gitForkBranch, dockerfileGitHubUtil);
        verify(all, times(0)).processErrorMessages(anyString(), anyString(), any());
        verify(all, times(1)).printSummary(anyList(), any());
    }

    @Test
    public void testAllCommandSkipsSendingPRsIfSearchRaisesException() throws Exception {
        Map<String, Object> nsMap = ImmutableMap.of(Constants.IMG,
                "image", Constants.TAG,
                "tag", Constants.STORE,
                "store");
        Namespace ns = new Namespace(nsMap);
        All all = spy(new All());
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        GitHubPullRequestSender pullRequestSender = mock(GitHubPullRequestSender.class);
        GitForkBranch gitForkBranch = mock(GitForkBranch.class);
        PullRequests pullRequests = mock(PullRequests.class);
        GitHubJsonStore imageTagStore = mock(GitHubJsonStore.class);
        ImageTagStoreContent imageTagStoreContent = mock(ImageTagStoreContent.class);

        List<ImageTagStoreContent> storeContents = mock(LinkedList.class);



        when(dockerfileGitHubUtil.getGitHubJsonStore("store")).thenReturn(imageTagStore);
        when(imageTagStore.getStoreContent(dockerfileGitHubUtil, "store")).thenReturn(storeContents);


        Iterator<ImageTagStoreContent> imageTagStoreContentIterator = mock(Iterator.class);
        when(imageTagStoreContentIterator.next()).thenReturn(imageTagStoreContent);
        when(imageTagStoreContentIterator.hasNext()).thenReturn(true, false);
        when(storeContents.iterator()).thenReturn(imageTagStoreContentIterator);
        when(imageTagStoreContent.getImageName()).thenReturn("image1");
        when(imageTagStoreContent.getTag()).thenReturn("tag1");
        when(all.getPullRequestSender(dockerfileGitHubUtil, ns)).thenReturn(pullRequestSender);
        when(all.getGitForkBranch("image1", "tag1", ns)).thenReturn(gitForkBranch);
        when(all.getPullRequests()).thenReturn(pullRequests);
        PagedSearchIterable<GHContent> contentsWithImage = mock(PagedSearchIterable.class);
        doNothing().when(pullRequests).prepareToCreate(ns, pullRequestSender,
                contentsWithImage, gitForkBranch, dockerfileGitHubUtil);
        when(dockerfileGitHubUtil.findFilesWithImage(anyString(), anyMap(),  anyInt())).thenThrow(new GHException("some exception"));


        all.execute(ns, dockerfileGitHubUtil);
        verify(all, times(1)).getGitForkBranch(anyString(), anyString(), any());
        verify(all, times(1)).getPullRequestSender(dockerfileGitHubUtil, ns);
        verify(all, times(1)).getPullRequests();
        verify(pullRequests, times(0)).prepareToCreate(ns, pullRequestSender,
                contentsWithImage, gitForkBranch, dockerfileGitHubUtil);
        verify(all, times(1)).processErrorMessages(anyString(), anyString(), any());
        verify(all, times(1)).printSummary(anyList(), any());
    }

    @Test
    public void testAllCommandSkipsSendingPRsIfPRCreationRaisesException() throws Exception {
        Map<String, Object> nsMap = ImmutableMap.of(Constants.IMG,
                "image", Constants.TAG,
                "tag", Constants.STORE,
                "store");
        Namespace ns = new Namespace(nsMap);
        All all = spy(new All());
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        GitHubPullRequestSender pullRequestSender = mock(GitHubPullRequestSender.class);
        GitForkBranch gitForkBranch = mock(GitForkBranch.class);
        PullRequests pullRequests = mock(PullRequests.class);
        GitHubJsonStore imageTagStore = mock(GitHubJsonStore.class);
        ImageTagStoreContent imageTagStoreContent = mock(ImageTagStoreContent.class);

        List<ImageTagStoreContent> storeContents = mock(LinkedList.class);
        when(dockerfileGitHubUtil.getGitHubJsonStore("store")).thenReturn(imageTagStore);
        when(imageTagStore.getStoreContent(dockerfileGitHubUtil, "store")).thenReturn(storeContents);


        Iterator<ImageTagStoreContent> imageTagStoreContentIterator = mock(Iterator.class);
        when(imageTagStoreContentIterator.next()).thenReturn(imageTagStoreContent);
        when(imageTagStoreContentIterator.hasNext()).thenReturn(true, false);
        when(storeContents.iterator()).thenReturn(imageTagStoreContentIterator);
        when(imageTagStoreContent.getImageName()).thenReturn("image1");
        when(imageTagStoreContent.getTag()).thenReturn("tag1");
        when(all.getPullRequestSender(dockerfileGitHubUtil, ns)).thenReturn(pullRequestSender);
        when(all.getGitForkBranch("image1", "tag1", ns)).thenReturn(gitForkBranch);
        when(all.getPullRequests()).thenReturn(pullRequests);
        PagedSearchIterable<GHContent> contentsWithImage = mock(PagedSearchIterable.class);
        List<PagedSearchIterable<GHContent>> contentsWithImageList = Collections.singletonList(contentsWithImage);
        Optional<List<PagedSearchIterable<GHContent>>> optionalContentsWithImageList = Optional.of(contentsWithImageList);
        doThrow(new IOException()).when(pullRequests).prepareToCreate(ns, pullRequestSender,
                contentsWithImage, gitForkBranch, dockerfileGitHubUtil);
        when(dockerfileGitHubUtil.findFilesWithImage(anyString(), anyMap(),  anyInt())).thenReturn(optionalContentsWithImageList);


        all.execute(ns, dockerfileGitHubUtil);
        verify(all, times(1)).getGitForkBranch(anyString(), anyString(), any());
        verify(all, times(1)).getPullRequestSender(dockerfileGitHubUtil, ns);
        verify(all, times(1)).getPullRequests();
        verify(pullRequests, times(1)).prepareToCreate(ns, pullRequestSender,
                contentsWithImage, gitForkBranch, dockerfileGitHubUtil);
        verify(all, times(1)).processErrorMessages(anyString(), anyString(), any());
        verify(all, times(1)).printSummary(anyList(), any());
    }

    @Test
    public void testProcessErrorMessages() {
        All all = new All();
        Exception exception = mock(Exception.class);
        ProcessingErrors processingErrors = new ProcessingErrors("imageName", "tag", Optional.of(exception));
        assertEquals(processingErrors.getClass(), all.processErrorMessages("imageName", "tag", Optional.of(exception)).getClass());
    }

    @Test
    public void testPrintSummaryWhenImagesWereMissed() {
        ProcessingErrors processingErrors = mock(ProcessingErrors.class);
        All all = spy(new All());
        List<ProcessingErrors> processingErrorsList = Collections.singletonList(processingErrors);
        Integer numberOfImagesToProcess = 2;
        Exception failure = mock(Exception.class);
        when(processingErrors.getFailure()).thenReturn(Optional.of(failure));

        all.printSummary(processingErrorsList, numberOfImagesToProcess);

        verify(processingErrors, times(1)).getImageName();
        verify(processingErrors, times(1)).getTag();
        verify(processingErrors, times(2)).getFailure();
        assertEquals(numberOfImagesToProcess, 2);
    }

    @Test
    public void testPrintSummaryWhenAllImagesWereSuccessfullyProcessed() {
        ProcessingErrors processingErrors = mock(ProcessingErrors.class);
        All all = spy(new All());
        List<ProcessingErrors> processingErrorsList = Collections.emptyList();
        Integer numberOfImagesToProcess = 2;

        all.printSummary(processingErrorsList, numberOfImagesToProcess);

        verify(processingErrors, times(0)).getImageName();
        verify(processingErrors, times(0)).getTag();
        verify(processingErrors, times(0)).getFailure();
        assertEquals(numberOfImagesToProcess, 2);
    }

    @Test
    public void testGetCommon() {
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
        All all = spy(new All());
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
        All all = spy(new All());
        ForkableRepoValidator forkableRepoValidator = mock(ForkableRepoValidator.class);
        GitHubPullRequestSender gitHubPullRequestSender =
                new GitHubPullRequestSender(dockerfileGitHubUtil, forkableRepoValidator, "");

        assertEquals(all.getPullRequestSender(dockerfileGitHubUtil, ns).getClass(),
                gitHubPullRequestSender.getClass());
    }
}
