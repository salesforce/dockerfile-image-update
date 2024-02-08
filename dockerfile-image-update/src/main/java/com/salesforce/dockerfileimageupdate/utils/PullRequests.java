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
                            && (isRenovateEnabled(Constants.RENOVATE_CONFIG_FILEPATHS, forkWithContentPaths.get()))) {
                        log.info("Found a renovate configuration file in the repo %s. Skip sending DFIU PRs to this repository.", forkWithContentPaths.get().getParent().getFullName());
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
     * (1) The presence of a file where renovate configurations are stored in the repository
     * (2) Ensuring that the file does not have the key "enabled" set to "false"
     * @param filePaths the list that contains all the names of the files that we are searching for in the repo
     * @param fork A GitHubContentToProcess object that contains the fork repository that is under process
     * @return true if the file is found in the path specified and is not disabled, false otherwise
     */
    private boolean isRenovateEnabled(List<String> filePaths, GitHubContentToProcess fork) throws IOException {
        for (String filePath : filePaths) {
            try {
                GHContent fileContent = fork.getParent().getFileContent(filePath);
                InputStream is = fileContent.read();
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(is));
                JSONTokener tokener = new JSONTokener(bufferedReader);
                JSONObject json = new JSONObject(tokener);
                is.close();
                bufferedReader.close();
                //If the file has the key 'enabled' set to false, it indicates that while the repo has been onboarded to renovate, it has been disabled for some reason
                return json.optBoolean("enabled", true);
            } catch (FileNotFoundException e) {
                log.debug("The file with name %s not found in the repository.", filePath);
            }
        }
        return false;
    }
}

