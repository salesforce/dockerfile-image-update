package com.salesforce.dockerfileimageupdate.utils;

import com.google.common.collect.*;
import com.salesforce.dockerfileimageupdate.model.*;
import com.salesforce.dockerfileimageupdate.process.*;
import net.sourceforge.argparse4j.inf.*;
import org.kohsuke.github.*;
import org.slf4j.*;

import java.io.*;
import java.util.*;

public class PullRequests {
    private static final Logger log = LoggerFactory.getLogger(PullRequests.class);
    public void prepareToCreate(final Namespace ns,
                                            GitHubPullRequestSender pullRequestSender,
                                            PagedSearchIterable<GHContent> contentsFoundWithImage,
                                            GitForkBranch gitForkBranch,
                                            DockerfileGitHubUtil dockerfileGitHubUtil,
                                            RateLimiter rateLimiter) throws IOException {
        Multimap<String, GitHubContentToProcess> pathToDockerfilesInParentRepo =
                pullRequestSender.forkRepositoriesFoundAndGetPathToDockerfiles(contentsFoundWithImage, gitForkBranch);
        List<IOException> exceptions = new ArrayList<>();
        List<String> skippedRepos = new ArrayList<>();
        for (String currUserRepo : pathToDockerfilesInParentRepo.keySet()) {
            Optional<GitHubContentToProcess> forkWithContentPaths =
                    pathToDockerfilesInParentRepo.get(currUserRepo).stream().findFirst();
            if (forkWithContentPaths.isPresent()) {
                try {
                    dockerfileGitHubUtil.changeDockerfiles(ns,
                            pathToDockerfilesInParentRepo,
                            forkWithContentPaths.get(), skippedRepos, gitForkBranch, rateLimiter);
                } catch (IOException | InterruptedException e) {
                    log.error(String.format("Error changing Dockerfile for %s", forkWithContentPaths.get().getParent().getFullName()), e);
                    exceptions.add((IOException) e);
                }
            } else {
                log.warn("Didn't find fork for {} so not changing Dockerfiles", currUserRepo);
            }
        }
        ResultsProcessor.processResults(skippedRepos, exceptions, log);
    }
}

