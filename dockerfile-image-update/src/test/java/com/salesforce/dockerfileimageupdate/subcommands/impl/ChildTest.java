/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.subcommands.impl;

import com.google.common.collect.ImmutableMap;
import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;
import net.sourceforge.argparse4j.inf.Namespace;
import org.kohsuke.github.GHRepository;
import org.mockito.Mockito;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static com.salesforce.dockerfileimageupdate.utils.Constants.*;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;

/**
 * Created by minho.park on 7/19/16.
 */
public class ChildTest {

    @DataProvider
    public Object[][] inputMap() {
        return new Object[][] {
                {Collections.emptyMap()},
                {ImmutableMap.of(
                        GIT_REPO, "test",
                        IMG, "test",
                        FORCE_TAG, "test")},
                {ImmutableMap.of(
                        GIT_REPO, "test",
                        IMG, "test",
                        FORCE_TAG, "test",
                        STORE, "test")},
        };
    }

    @Test(dataProvider = "inputMap")
    public void checkPullRequestMade(Map<String, Object> inputMap) throws Exception {
        Child child = new Child();
        Namespace ns = new Namespace(inputMap);
        DockerfileGitHubUtil dockerfileGitHubUtil = Mockito.mock(DockerfileGitHubUtil.class);
        Mockito.when(dockerfileGitHubUtil.getRepo(Mockito.any())).thenReturn(new GHRepository());
        Mockito.when(dockerfileGitHubUtil.getOrCreateFork(Mockito.any())).thenReturn(new GHRepository());
        doNothing().when(dockerfileGitHubUtil).modifyAllOnGithub(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
        doNothing().when(dockerfileGitHubUtil).updateStore(anyString(), anyString(), anyString());
        doNothing().when(dockerfileGitHubUtil).createPullReq(Mockito.any(), anyString(), Mockito.any(), anyString());

        child.execute(ns, dockerfileGitHubUtil);

        Mockito.verify(dockerfileGitHubUtil, atLeastOnce())
                .createPullReq(Mockito.any(), anyString(), Mockito.any(), anyString());
    }

    @Test
    public void testCreateForkFailureCase_CreatePullReqIsSkipped() throws IOException, InterruptedException {
        Child child = new Child();
        Map<String, Object> nsMap = ImmutableMap.of(
                GIT_REPO, "test",
                IMG, "test",
                FORCE_TAG, "test",
                STORE, "test");
        Namespace ns = new Namespace(nsMap);
        DockerfileGitHubUtil dockerfileGitHubUtil = Mockito.mock(DockerfileGitHubUtil.class);
        Mockito.when(dockerfileGitHubUtil.getRepo(Mockito.any())).thenReturn(new GHRepository());
        Mockito.when(dockerfileGitHubUtil.getOrCreateFork(Mockito.any())).thenReturn(null);
        child.execute(ns, dockerfileGitHubUtil);
        Mockito.verify(dockerfileGitHubUtil, Mockito.never()).createPullReq(Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.any());
    }
}