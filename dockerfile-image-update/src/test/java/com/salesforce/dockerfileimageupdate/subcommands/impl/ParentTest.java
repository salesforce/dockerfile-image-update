/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.subcommands.impl;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.salesforce.dockerfileimageupdate.model.GitHubContentToProcess;
import com.salesforce.dockerfileimageupdate.utils.Constants;
import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;
import net.sourceforge.argparse4j.inf.Namespace;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

/**
 * Created by minho.park on 7/19/16.
 */
public class ParentTest {

    @Test
    public void testChangeDockerfiles_pullRequestCreation() throws Exception {
        Map<String, Object> nsMap = ImmutableMap.of(Constants.IMG, "image", Constants.TAG, "tag", Constants.STORE, "store");
        Namespace ns = new Namespace(nsMap);

        GHRepository forkedRepo = mock(GHRepository.class);
        when(forkedRepo.isFork()).thenReturn(true);
        when(forkedRepo.getFullName()).thenReturn("forkedrepo");

        GHRepository parentRepo = mock(GHRepository.class);
        when(parentRepo.getFullName()).thenReturn("repo2");
        when(forkedRepo.getParent()).thenReturn(parentRepo);

        Multimap<String, GitHubContentToProcess> pathToDockerfilesInParentRepo = HashMultimap.create();
        pathToDockerfilesInParentRepo.put("repo1", new GitHubContentToProcess(null, null, "df1"));
        pathToDockerfilesInParentRepo.put("repo2", new GitHubContentToProcess(null, null, "df2"));
        pathToDockerfilesInParentRepo.put("repo3", new GitHubContentToProcess(null, null, "df3"));
        pathToDockerfilesInParentRepo.put("repo4", new GitHubContentToProcess(null, null, "df4"));

        GHContent content = mock(GHContent.class);

        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);

        when(dockerfileGitHubUtil.getPullRequestForImageBranch(eq(forkedRepo), any())).thenReturn(Optional.empty());

        when(dockerfileGitHubUtil.tryRetrievingContent(forkedRepo, "df2",
                "image-tag")).thenReturn(content);

        Parent parent = new Parent();
        parent.loadDockerfileGithubUtil(dockerfileGitHubUtil);
        parent.changeDockerfiles(ns, pathToDockerfilesInParentRepo, new GitHubContentToProcess(forkedRepo, parentRepo, ""), new ArrayList<>());

        Mockito.verify(dockerfileGitHubUtil, times(1))
                .tryRetrievingContent(eq(forkedRepo), eq("df2"), eq("image-tag"));
        Mockito.verify(dockerfileGitHubUtil, times(1))
                .modifyOnGithub(eq(content), eq("image-tag"), eq("image"), eq("tag"), anyString());
        Mockito.verify(dockerfileGitHubUtil, times(1))
                .createPullReq(eq(parentRepo), eq("image-tag"), eq(forkedRepo), any());
    }

    @Test
    public void testOnePullRequestForMultipleDockerfilesInSameRepo() throws Exception {
        Map<String, Object> nsMap = ImmutableMap.of(Constants.IMG,
                "image", Constants.TAG,
                "tag", Constants.STORE,
                "store");
        Namespace ns = new Namespace(nsMap);

        Multimap<String, GitHubContentToProcess> pathToDockerfilesInParentRepo = HashMultimap.create();
        pathToDockerfilesInParentRepo.put("repo1", new GitHubContentToProcess(null, null, "df11"));
        pathToDockerfilesInParentRepo.put("repo1", new GitHubContentToProcess(null, null, "df12"));
        pathToDockerfilesInParentRepo.put("repo3", new GitHubContentToProcess(null, null, "df3"));
        pathToDockerfilesInParentRepo.put("repo4", new GitHubContentToProcess(null, null, "df4"));

        GHRepository forkedRepo = mock(GHRepository.class);
        when(forkedRepo.isFork()).thenReturn(true);
        when(forkedRepo.getFullName()).thenReturn("forkedrepo");

        GHRepository parentRepo = mock(GHRepository.class);
        when(parentRepo.getFullName()).thenReturn("repo1");
        when(forkedRepo.getParent()).thenReturn(parentRepo);

        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        when(dockerfileGitHubUtil.getPullRequestForImageBranch(any(), any())).thenReturn(Optional.empty());
        when(dockerfileGitHubUtil.getRepo(forkedRepo.getFullName())).thenReturn(forkedRepo);
        GHContent forkedRepoContent1 = mock(GHContent.class);
        when(dockerfileGitHubUtil.tryRetrievingContent(eq(forkedRepo),
                eq("df11"), eq("image-tag"))).thenReturn(forkedRepoContent1);
        GHContent forkedRepoContent2 = mock(GHContent.class);
        when(dockerfileGitHubUtil.tryRetrievingContent(eq(forkedRepo),
                eq("df12"), eq("image-tag"))).thenReturn(forkedRepoContent2);

        Parent parent = new Parent();
        parent.loadDockerfileGithubUtil(dockerfileGitHubUtil);
        parent.changeDockerfiles(ns, pathToDockerfilesInParentRepo, new GitHubContentToProcess(forkedRepo, parentRepo, ""), new ArrayList<>());

        // Both Dockerfiles retrieved from the same repo
        Mockito.verify(dockerfileGitHubUtil, times(1)).tryRetrievingContent(eq(forkedRepo),
                eq("df11"), eq("image-tag"));
        Mockito.verify(dockerfileGitHubUtil, times(1)).tryRetrievingContent(eq(forkedRepo),
                eq("df12"), eq("image-tag"));

        // Both Dockerfiles modified
        Mockito.verify(dockerfileGitHubUtil, times(2))
                .modifyOnGithub(any(), eq("image-tag"), eq("image"), eq("tag"), anyString());

        // Only one PR created on the repo with changes to both Dockerfiles.
        Mockito.verify(dockerfileGitHubUtil, times(1)).createPullReq(eq(parentRepo),
                eq("image-tag"), eq(forkedRepo), any());
    }

    @Test
    public void testNoPullRequestForMissingDockerfile() throws Exception {
        Map<String, Object> nsMap = ImmutableMap.of(Constants.IMG,
                "image", Constants.TAG,
                "tag", Constants.STORE,
                "store");
        Namespace ns = new Namespace(nsMap);

        Multimap<String, GitHubContentToProcess> pathToDockerfilesInParentRepo = HashMultimap.create();
        pathToDockerfilesInParentRepo.put("repo1", new GitHubContentToProcess(null, null, "missing_df"));

        GHRepository forkedRepo = mock(GHRepository.class);
        when(forkedRepo.isFork()).thenReturn(true);
        when(forkedRepo.getFullName()).thenReturn("forkedrepo");

        GHRepository parentRepo = mock(GHRepository.class);
        when(parentRepo.getFullName()).thenReturn("repo1");
        when(forkedRepo.getParent()).thenReturn(parentRepo);

        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        when(dockerfileGitHubUtil.getPullRequestForImageBranch(any(), any())).thenReturn(Optional.empty());
        // Dockerfile not found anymore when trying to retrieve contents from the forked repo.
        when(dockerfileGitHubUtil.tryRetrievingContent(eq(forkedRepo),
                eq("missing_df"), eq("image-tag"))).thenReturn(null);

        Parent parent = new Parent();
        parent.loadDockerfileGithubUtil(dockerfileGitHubUtil);
        parent.changeDockerfiles(ns, pathToDockerfilesInParentRepo, new GitHubContentToProcess(forkedRepo, parentRepo, ""), new ArrayList<>());

        // trying to retrieve Dockerfile
        Mockito.verify(dockerfileGitHubUtil, times(1)).tryRetrievingContent(eq(forkedRepo),
                eq("missing_df"), eq("image-tag"));

        // missing Dockerfile, so skipping modify and create PR
        Mockito.verify(dockerfileGitHubUtil, times(0))
                .modifyOnGithub(any(), anyString(), anyString(), anyString(), anyString());
        Mockito.verify(dockerfileGitHubUtil, times(0)).createPullReq(eq(parentRepo),
                anyString(), eq(forkedRepo), any());
    }
}