/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate;

import com.google.common.reflect.ClassPath;
import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;

import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.extras.okhttp3.OkHttpGitHubConnector;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import net.sourceforge.argparse4j.inf.Namespace;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.salesforce.dockerfileimageupdate.utils.Constants.GIT_API;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

/**
 * Created by afalko on 10/25/17.
 */
public class CommandLineTest {
    @Test
    public void testLoadSubcommands() throws Exception {
        Set<ClassPath.ClassInfo> classes = CommandLine.findSubcommands(CommandLine.getArgumentParser());
        List<String> expectedSubCommands = Arrays.asList("All", "Parent", "Child");
        assertEquals(classes.size(), expectedSubCommands.size());
        classes.forEach(
                classInfo -> assertThat(expectedSubCommands).contains(classInfo.getSimpleName())
        );
    }

    @Test
    public void testShouldAddWireLoggerDisabled() {
      GitHubBuilder builder = Mockito.spy(new GitHubBuilder());
      CommandLine.shouldAddWireLogger(builder, false);

      Mockito.verify(builder, never()).withConnector(Mockito.any(OkHttpGitHubConnector.class));
    }

    @Test
    public void testShouldAddWireLoggerEnabled() {
      GitHubBuilder builder = Mockito.spy(new GitHubBuilder());
      CommandLine.shouldAddWireLogger(builder, true);

      Mockito.verify(builder).withConnector(Mockito.any(OkHttpGitHubConnector.class));
    }

    @Test
    public void testInitializeDockerfileGithubUtil() throws IOException {
      GitHubBuilder builder = Mockito.spy(new GitHubBuilder());
      GitHub github = Mockito.mock(GitHub.class);

      Mockito.doReturn(github).when(builder).build();

      DockerfileGitHubUtil retval = CommandLine.initializeDockerfileGithubUtil("someUrl", "someToken", () -> builder, true);

      Mockito.verify(builder).withEndpoint(Mockito.eq("someUrl"));
      Mockito.verify(builder).withOAuthToken(Mockito.eq("someToken"));
      Mockito.verify(builder).withConnector(Mockito.any(OkHttpGitHubConnector.class));
      Mockito.verify(github).checkApiUrlValidity();
      assertSame(retval.getGitHubUtil().getGithub(), github);
    }

    @Test
    public void testGitApiUrlNonNullNamespace() throws IOException {
      Namespace ns = Mockito.mock(Namespace.class);
      Mockito.doReturn("someUrl").when(ns).getString(GIT_API);

      String retval = CommandLine.gitApiUrl(ns, x -> null);
      assertEquals(retval, "someUrl");
    }

    @Test
    public void testGitApiUrlNullNamespace() throws IOException {
      Namespace ns = Mockito.mock(Namespace.class);
      Mockito.doReturn(null).when(ns).getString(GIT_API);

      String retval = CommandLine.gitApiUrl(ns, x -> "anotherUrl");
      assertEquals(retval, "anotherUrl");
    }

    @Test(expectedExceptions = { IOException.class } )
    public void testGitApiUrlNamespaceAndEnvNull() throws IOException {
      Namespace ns = Mockito.mock(Namespace.class);
      Mockito.doReturn(null).when(ns).getString(GIT_API);

      String retval = CommandLine.gitApiUrl(ns, x -> null);
      assertEquals(retval, "anotherUrl");
    }

    @Test
    public void gitApiTokenAvailable() {
      Function<String, String> envFunc = x -> {
        assertEquals(x, "git_api_token");
        return "token";
      };

      Consumer<Integer> exitFunc = new Consumer<Integer>() {
        @Override
        public void accept(Integer t) {}
      };

      final String retval = CommandLine.gitApiToken(envFunc, exitFunc);
      assertEquals(retval, "token");
    }

    @Test
    public void gitApiTokenNull() {
      Function<String, String> envFunc = x -> {
        assertEquals(x, "git_api_token");
        return null;
      };

      Consumer<Integer> exitFunc = x -> assertEquals(x, 3);

      CommandLine.gitApiToken(envFunc, exitFunc);
    }
}