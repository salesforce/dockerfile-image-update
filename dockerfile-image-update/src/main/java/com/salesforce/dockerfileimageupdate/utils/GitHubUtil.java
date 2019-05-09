/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.utils;

import com.google.common.collect.Multimap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.kohsuke.github.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Created by minho-park on 7/1/16.
 */
public class GitHubUtil {
    private static final Logger log = LoggerFactory.getLogger(GitHubUtil.class);

    private final GitHub github;

    public GitHubUtil(GitHub gitHub) throws IOException {
        github = gitHub;
    }

    public GitHub getGithub() {
        return github;
    }

    public GHRepository getRepo(String repo) throws IOException {
        return github.getRepository(repo);
    }

    /**
     * Create a public repo. We cannot make private repos for open source users because they'd be forced to buy a
     * paid account.
     *
     * @param repoName Name of the repository
     * @return
     * @throws IOException
     */
    public GHRepository createPublicRepo(String repoName) throws IOException {
        GHCreateRepositoryBuilder repoBuilder = github.createRepository(repoName);
        repoBuilder.private_(false);
        return repoBuilder.create();
    }

    public GHMyself getMyself() throws IOException {
        return github.getMyself();
    }

    public GHContentSearchBuilder startSearch() { return github.searchContent(); }

    /* Create a fork. Does not check if the fork is already there, because the fork will not be created if a fork
     * already exists.
     */
    public GHRepository createFork(GHRepository repo) {
        try {
            return repo.fork();
        } catch (IOException e) {
            log.error("Could not fork {}", repo.getFullName(), e);
        }
        return null;
    }

    public void safeDeleteRepo(GHRepository repo) throws IOException {
        try {
            repo.delete();
        } catch (IOException e) {
            throw new IOException("Please verify that the GitHub token " +
                    "provided has access to deleting repositories.");
        }
    }

    public int createPullReq(GHRepository origRepo, String branch,
                                 GHRepository forkRepo, String title, String body) throws InterruptedException {
        log.info("Creating Pull Request on {} from {}...", origRepo.getFullName(), forkRepo.getFullName());
        //TODO: if no commits, pull request will fail, but this doesn't handle that.
        try {
            origRepo.createPullRequest(title, forkRepo.getOwnerName() + ":" + branch,
                    origRepo.getDefaultBranch(), body);
//            origRepo.createPullRequest("Update base image in Dockerfile", forkRepo.getOwnerName() + ":" + branch,
//                    origRepo.getDefaultBranch(), "Automatic Dockerfile Image Updater. Please merge.");
            log.info("A pull request has been created. Please check on Github.");
            return 0;
        } catch (IOException e) {
            log.warn("Handling error with pull request creation...");
            JsonElement root = new JsonParser().parse(e.getMessage());
            JsonArray errorJson = root.getAsJsonObject().get("errors").getAsJsonArray();
            String error = errorJson.get(0).getAsJsonObject().get("message").getAsString();
            log.info("error: {}", error);
            if (error.startsWith("A pull request already exists")) {
                log.info("NOTE: {} New commits may have been added to the pull request.", error);
                return 0;
            } else if (error.startsWith("No commits between")) {
                log.warn("NOTE: {} Pull request was not created.", error);
                return 1;
            } else {
                log.warn("An error occurred in pull request: {} Trying again...", error);
                Thread.sleep(3000);
                return -1;
            }
        }
    }

    public GHRepository tryRetrievingRepository(String repoName) throws InterruptedException {
        GHRepository repo = null;
        for (int i = 0; i < 10; i++) {
            try {
                repo = github.getRepository(repoName);
                break;
            } catch (IOException e1) {
                log.warn("Repository not created yet. Retrying connection to repository...");
                Thread.sleep(1000);
            }
        }
        return repo;
    }

    public GHContent tryRetrievingContent(GHRepository repo, String path, String branch) throws InterruptedException {
        /* There are issues with the Github api returning that the Github repository exists, but has no content,
         * when we try to pull on it the moment it is created. The system must wait a short time before we can move on.
         */
        GHContent content = null;
        for (int i = 0; i < 10; i++) {
            try {
                content = repo.getFileContent(path, branch);
                break;
            } catch (IOException e1) {
                log.warn("Content in repository not created yet. Retrying connection to fork...");
                Thread.sleep(1000);
            }
        }
        return content;
    }

    /* Workaround: The GitHub API caches API calls for up to 60 seconds, so back-to-back API calls with the same
     * command will return the same thing. i.e. the above command listRepositories will return the same output if
     * this tool is invoked twice in a row, even though it should return different lists, because of the new forks.
     *
     * The GitHub API itself actually provides a workaround: check
     * https://developer.github.com/guides/getting-started/#conditional-requests
     * However, the GitHub API library uses an outdated version of Okhttp, and Okhttp no longer supports
     * OkUrlFactory, which is required to specify the cache. In other words, we cannot flush the cache.
     *
     * Instead, we wait for 60 seconds if the list retrieved is not the list we want.
     */
    public PagedIterable<GHRepository> getGHRepositories(Multimap<String, String> pathToDockerfileInParentRepo,
                                                         GHMyself currentUser) throws InterruptedException {
        PagedIterable<GHRepository> listOfRepos;
        Set<String> repoNamesSet = new HashSet<>();
        while (true) {
            listOfRepos = currentUser.listRepositories(100, GHMyself.RepositoryListFilter.OWNER);
            for (GHRepository repo : listOfRepos) {
                repoNamesSet.add(repo.getName());
            }
            boolean listOfReposHasRecentForks = true;
            for (String s : pathToDockerfileInParentRepo.keySet()) {
                String forkName = s.substring(s.lastIndexOf('/') + 1);
                log.info(forkName);
                if (!repoNamesSet.contains(forkName)) {
                    log.debug("Forking is still in progress for {}" , forkName);
                    listOfReposHasRecentForks = false;
                }
            }
            if (listOfReposHasRecentForks) {
                break;
            } else {
                log.info("Waiting for GitHub API cache to clear...");
                Thread.sleep(TimeUnit.MINUTES.toMillis(1));
            }
        }

        return listOfRepos;
    }
}
