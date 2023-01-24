/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.subcommands.impl;

import com.google.common.collect.ImmutableMap;
import com.salesforce.dockerfileimageupdate.storage.GitHubJsonStore;
import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;
import net.sourceforge.argparse4j.inf.Namespace;
import org.kohsuke.github.GHRepository;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import com.salesforce.dockerfileimageupdate.utils.RateLimiter;

import java.util.Map;

import static com.salesforce.dockerfileimageupdate.utils.Constants.*;
import static org.mockito.Mockito.*;

/**
 * Created by minho.park on 7/19/16.
 */
public class ChildTest {

    @DataProvider
    public Object[][] inputMap() {
        return new Object[][] {
                {ImmutableMap.of(
                        GIT_REPO, "test",
                        IMG, "test",
                        FORCE_TAG, "test",
                        STORE, "test")},
                {ImmutableMap.of(
                        GIT_REPO, "test",
                        IMG, "test",
                        FORCE_TAG, "test",
                        STORE, "test")},
        };
    }

    @Test(dataProvider = "inputMap")
    public void checkPullRequestMade(Map<String, Object> inputMap) throws Exception {
        Child child = spy(new Child());
        Namespace ns = new Namespace(inputMap);
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        RateLimiter rateLimiter = new RateLimiter();
        GitHubJsonStore imageTagStore = mock(GitHubJsonStore.class);
        when(dockerfileGitHubUtil.getRepo(any())).thenReturn(new GHRepository());
        when(dockerfileGitHubUtil.getOrCreateFork(any())).thenReturn(new GHRepository());
        doNothing().when(dockerfileGitHubUtil).modifyAllOnGithub(any(), any(), any(), any(), any());

        when(dockerfileGitHubUtil.getGitHubJsonStore("test")).thenReturn(imageTagStore);

        doNothing().when(dockerfileGitHubUtil).createPullReq(any(), anyString(), any(), any(), eq(rateLimiter));
        try (MockedStatic<RateLimiter> mockedRateLimiter = Mockito.mockStatic(RateLimiter.class)) {
            mockedRateLimiter.when(() -> RateLimiter.getInstance(ns))
                    .thenReturn(rateLimiter);
            child.execute(ns, dockerfileGitHubUtil);

            verify(dockerfileGitHubUtil, times(1))
                    .createPullReq(any(), anyString(), any(), any(), any(RateLimiter.class));
        }
    }

    @Test
    public void checkPullRequestMadeForS3ImageStore() throws Exception {
        Child child = spy(new Child());
        Map<String, Object> nsMap = ImmutableMap.of(
                GIT_REPO, "test",
                IMG, "test",
                FORCE_TAG, "test",
                STORE, "s3://test"
                );
        Namespace ns = new Namespace(nsMap);
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);

        when(dockerfileGitHubUtil.getRepo(any())).thenReturn(new GHRepository());
        when(dockerfileGitHubUtil.getOrCreateFork(any())).thenReturn(new GHRepository());
        doNothing().when(dockerfileGitHubUtil).modifyAllOnGithub(any(), any(), any(), any(), any());
        doNothing().when(dockerfileGitHubUtil).createPullReq(any(), anyString(), any(), any(),eq(null));

        child.execute(ns, dockerfileGitHubUtil);

        verify(dockerfileGitHubUtil, times(1))
                .createPullReq(any(), anyString(), any(), any(), eq(null));
    }

    @Test
    public void testCreateForkFailureCase_CreatePullReqIsSkipped() throws Exception {
        Child child = spy(new Child());
        Map<String, Object> nsMap = ImmutableMap.of(
                GIT_REPO, "test",
                IMG, "test",
                FORCE_TAG, "test",
                STORE, "test");
        Namespace ns = new Namespace(nsMap);
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        RateLimiter rateLimiter = spy(new RateLimiter());
        when(dockerfileGitHubUtil.getRepo(any())).thenReturn(new GHRepository());
        when(dockerfileGitHubUtil.getOrCreateFork(any())).thenReturn(null);
        GitHubJsonStore imageTagStore = mock(GitHubJsonStore.class);
        when(dockerfileGitHubUtil.getGitHubJsonStore("test")).thenReturn(imageTagStore);
        child.execute(ns, dockerfileGitHubUtil);
        verify(dockerfileGitHubUtil, never()).createPullReq(any(), any(), any(),
                any(), eq(rateLimiter));
    }
}
