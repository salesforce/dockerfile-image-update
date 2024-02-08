package com.salesforce.dockerfileimageupdate.utils;

import com.google.common.collect.*;
import com.salesforce.dockerfileimageupdate.model.*;
import com.salesforce.dockerfileimageupdate.process.*;
import net.sourceforge.argparse4j.inf.*;
import org.json.JSONObject;
import org.json.JSONTokener;
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
                    //If the repository has been onboarded to renovate enterprise, skip sending the DFIU PR
                    if(ns.getBoolean(Constants.CHECK_FOR_RENOVATE)
                            && (isRenovateEnabled(Constants.RENOVATE_CONFIG_FILEPATH, forkWithContentPaths.get()))) {
                        log.info("Found file with name %s in the repo %s. Skip sending DFIU PRs to this repository.", Constants.RENOVATE_CONFIG_FILEPATH, forkWithContentPaths.get().getParent().getFullName());
                    } else {
                        dockerfileGitHubUtil.changeDockerfiles(ns,
                                pathToDockerfilesInParentRepo,
                                forkWithContentPaths.get(), skippedRepos,
                                gitForkBranch, rateLimiter);
                    }
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

    /**
     * Check if the repository is onboarded to Renovate. The signal we are looking for are
     * (1) The presence of a file named renovate.json in the root of the repository
     * (2) Ensuring that the file does not have the key "enabled" set to "false"
     * @param filePath the name of the file that we are searching for in the repo
     * @param fork A GitHubContentToProcess object that contains the fork repository that is under process
     * @return true if the file is found in the path specified and is not disabled, false otherwise
     */
    private boolean isRenovateEnabled(String filePath, GitHubContentToProcess fork) throws IOException {
        try {
            GHContent fileContent = fork.getParent().getFileContent(filePath);
            InputStream is = fileContent.read();
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(is));
            JSONTokener tokener = new JSONTokener(bufferedReader);
            JSONObject json = new JSONObject(tokener);
            //If the renovate.json file has the key 'enabled' set to false, it indicates that while the repo has been onboarded to renovate, it has been disabled for some reason
            if (json.has("enabled") && json.getBoolean("enabled") == false) {
                return false;
            }
        } catch (FileNotFoundException e) {
            log.debug("The file with name %s not found in the repository.", filePath);
            return false;
        }
        return true;
    }
}

