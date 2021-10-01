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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Created by minho-park on 7/1/16.
 */
public class GitHubUtil {
    private static final Logger log = LoggerFactory.getLogger(GitHubUtil.class);
    public static final String NO_BRANCH_WARN_FORMAT = "Couldn't find branch `%s` in repo `%s`. Waiting a second...";

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
     * @return GHRepository for {@code repoName}
     * @throws IOException when failing to get a GHRepository
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

    /**
     * Attempt to delete the GitHub repo if there are no pull requests associated with it
     * @param repo the repo to delete
     */
    public void safeDeleteRepo(GHRepository repo) throws IOException {
        if (!repo.queryPullRequests().state(GHIssueState.OPEN).list().iterator().hasNext()) {
            try {
                repo.delete();
            } catch (IOException e) {
                throw new IOException("Please verify that the GitHub token " +
                        "provided has access to deleting repositories.");
            }
        } else {
            log.info("Fork has open pull requests. So we will not delete it: {}", repo);
        }
    }

    public int createPullReq(GHRepository origRepo, String branch,
                                 GHRepository forkRepo, String title, String body) throws InterruptedException {
        log.info("Creating Pull Request on {} from {}...", origRepo.getFullName(), forkRepo.getFullName());
        //TODO: if no commits, pull request will fail, but this doesn't handle that.
        try {
            GHPullRequest pullRequest = origRepo.createPullRequest(title, forkRepo.getOwnerName() + ":" + branch,
                    origRepo.getDefaultBranch(), body);
//            origRepo.createPullRequest("Update base image in Dockerfile", forkRepo.getOwnerName() + ":" + branch,
//                    origRepo.getDefaultBranch(), "Automatic Dockerfile Image Updater. Please merge.");
            log.info("A pull request has been created at {}", pullRequest.getHtmlUrl());
            return 0;
        } catch (IOException e) {
            log.warn("Handling error with pull request creation... {}", e.getMessage());
            JsonElement root = JsonParser.parseString(e.getMessage());
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
                // TODO: THIS WILL LOOP FOREVVEEEEEERRRR
                log.warn("An error occurred in pull request: {} Trying again...", error);
                waitFor(TimeUnit.SECONDS.toMillis(3));
                return -1;
            }
        }
    }

    /**
     * Attempt to find the provided {@code branch}. Generally, github-api will retry for us without
     * the need to do this. This particular process is useful when we need more time such as when
     * we are waiting for a repository to be forked.
     *
     * @param repo - wait until we can retrieve {@code branch from this repo}
     * @param branchName - the branch to wait for
     */
    protected GHBranch tryRetrievingBranch(GHRepository repo, String branchName) throws InterruptedException {
        for (int i = 0; i < 10; i++) {
            try {
                GHBranch branch = repo.getBranch(branchName);
                if (branch != null) {
                    return branch;
                }
                log.warn(String.format(NO_BRANCH_WARN_FORMAT, branchName, repo.getName()));
            } catch (IOException exception) {
                // Keep waiting - this is expected rather than a null branch but we'll handle
                // both scenarios as neither would indicate that the repo branch is ready
                log.warn(String.format(NO_BRANCH_WARN_FORMAT + " Exception was: %s",
                                branchName, repo.getName(), exception.getMessage()));
            }
            waitFor(TimeUnit.SECONDS.toMillis(1));
        }
        return null;
    }

    public GHRepository tryRetrievingRepository(String repoName) throws InterruptedException {
        GHRepository repo = null;
        for (int i = 0; i < 10; i++) {
            try {
                repo = github.getRepository(repoName);
                break;
            } catch (IOException e1) {
                log.warn("Repository not created yet. Retrying connection to repository...");
                waitFor(TimeUnit.SECONDS.toMillis(1));
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
                waitFor(TimeUnit.SECONDS.toMillis(1));
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
    public List<GHRepository> getGHRepositories(Multimap<String, String> pathToDockerfileInParentRepo,
                                                 GHMyself currentUser) throws InterruptedException {
        List<GHRepository> listOfRepos = new ArrayList<>();
        while (true) {
            Map<String, GHRepository> repoByName = getReposForUserAtCurrentInstant(currentUser);
            boolean listOfReposHasRecentForks = true;
            for (String s : pathToDockerfileInParentRepo.keySet()) {
                String forkName = s.substring(s.lastIndexOf('/') + 1);
                log.info(String.format("Verifying that %s has been forked", forkName));
                if (repoByName.containsKey(forkName)) {
                    listOfRepos.add(repoByName.get(forkName));
                } else {
                    log.debug("Forking is still in progress for {}" , forkName);
                    listOfReposHasRecentForks = false;
                }
            }
            if (listOfReposHasRecentForks) {
                break;
            } else {
                log.info("Waiting for GitHub API cache to clear...");
                waitFor(TimeUnit.MINUTES.toMillis(1));
            }
        }
        return listOfRepos;
    }

    protected void waitFor(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    /**
     * Check to see if the provided {@code repo} has the {@code branchName}
     *
     * @param repo - repo to check
     * @param branchName - branchName we're looking for in {@code repo}
     * @return {@code repo} has branch with name {@code branchName}
     * @throws IOException - there was some exception that we couldn't overcome
     */
    public boolean repoHasBranch(GHRepository repo, String branchName) throws IOException {
        try {
            GHBranch branch = repo.getBranch(branchName);
            return branch != null;
        } catch (GHFileNotFoundException exception) {
            return false;
        }
    }
    /**
     * Returns a <code>java.util.Map</code> of GitHub repositories owned by a user. Returned Map's keys are the repository
     * names and values are their corresponding GitHub repository objects.
     * @param user GitHub user (person/org) which has repositories
     * @return map of repo name to GHRepository
     */
    public Map<String, GHRepository> getReposForUserAtCurrentInstant(GHMyself user) {
        Map<String, GHRepository> repoByName = new HashMap<>();
        if (user == null) {
            return repoByName;
        }
        PagedIterable<GHRepository> reposIterator = user.listRepositories(100, GHMyself.RepositoryListFilter.OWNER);
        for (GHRepository repo: reposIterator) {
            repoByName.put(repo.getName(), repo);
        }
        return repoByName;
    }
}
