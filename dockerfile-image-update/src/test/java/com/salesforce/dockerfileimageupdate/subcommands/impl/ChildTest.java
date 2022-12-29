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
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import com.salesforce.dockerfileimageupdate.utils.RateLimiter;

import java.util.Map;

import static com.salesforce.dockerfileimageupdate.utils.Constants.*;
import static org.mockito.Matchers.anyString;
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
                        STORE, "test",
                        RATE_LIMIT_PR_CREATION, "500-per-60s")},
                {ImmutableMap.of(
                        GIT_REPO, "test",
                        IMG, "test",
                        FORCE_TAG, "test",
                        STORE, "test",
                        RATE_LIMIT_PR_CREATION, "500-per-60s")},
        };
    }

    @Test(dataProvider = "inputMap")
    public void checkPullRequestMade(Map<String, Object> inputMap) throws Exception {
        Child child = spy(new Child());
        Namespace ns = new Namespace(inputMap);
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        RateLimiter rateLimiter = spy(new RateLimiter(DEFAULT_RATE_LIMIT,DEFAULT_RATE_LIMIT_DURATION
                ,DEFAULT_TOKEN_ADDING_RATE));
        GitHubJsonStore imageTagStore = mock(GitHubJsonStore.class);
        when(dockerfileGitHubUtil.getRepo(any())).thenReturn(new GHRepository());
        when(dockerfileGitHubUtil.getOrCreateFork(any())).thenReturn(new GHRepository());
        doNothing().when(dockerfileGitHubUtil).modifyAllOnGithub(any(), any(), any(), any(), any());

        when(dockerfileGitHubUtil.getGitHubJsonStore("test")).thenReturn(imageTagStore);
        when(rateLimiter.getRateLimiter(ns)).thenReturn(rateLimiter);

        doNothing().when(dockerfileGitHubUtil).createPullReq(any(), anyString(), any(), any(), eq(rateLimiter));

        child.execute(ns, dockerfileGitHubUtil);

        verify(dockerfileGitHubUtil, times(1))
                .createPullReq(any(), anyString(), any(), any(), any(RateLimiter.class));
    }

    @Test
    public void checkPullRequestMadeForS3ImageStore() throws Exception {
        Child child = spy(new Child());
        Map<String, Object> nsMap = ImmutableMap.of(
                GIT_REPO, "test",
                IMG, "test",
                FORCE_TAG, "test",
                STORE, "s3://test"
               // USE_RATE_LIMITING, false);
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
                STORE, "test",
                RATE_LIMIT_PR_CREATION, "500-per-60s");
        Namespace ns = new Namespace(nsMap);
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        RateLimiter rateLimiter = spy(new RateLimiter(DEFAULT_RATE_LIMIT,DEFAULT_RATE_LIMIT_DURATION
                ,DEFAULT_TOKEN_ADDING_RATE));
        when(dockerfileGitHubUtil.getRepo(any())).thenReturn(new GHRepository());
        when(dockerfileGitHubUtil.getOrCreateFork(any())).thenReturn(null);
        when(rateLimiter.getRateLimiter(ns)).thenReturn(rateLimiter);
        GitHubJsonStore imageTagStore = mock(GitHubJsonStore.class);
        when(dockerfileGitHubUtil.getGitHubJsonStore("test")).thenReturn(imageTagStore);
        child.execute(ns, dockerfileGitHubUtil);
        verify(dockerfileGitHubUtil, never()).createPullReq(any(), any(), any(),
                any(), eq(rateLimiter));
    }
}
