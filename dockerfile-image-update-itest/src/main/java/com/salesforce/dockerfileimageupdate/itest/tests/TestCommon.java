/*
 * Copyright (c) 2017, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.itest.tests;

import com.salesforce.dockerfileimageupdate.utils.GitHubUtil;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.PagedIterable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by afalko on 10/19/17.
 */
public class TestCommon {
    private static final Logger log = LoggerFactory.getLogger(TestCommon.class);

    public static final List<String> ORGS = Arrays.asList(
            "dockerfile-image-update-itest", "dockerfile-image-update-itest-2", "dockerfile-image-update-itest-3");

    public static void initializeRepos(GHOrganization org, List<String> repos, String image,
                                       List<GHRepository> createdRepos, GitHubUtil gitHubUtil) throws Exception {
        for (String repoName : repos) {
            GHRepository repo = org.createRepository(repoName)
                    .description("Delete if this exists. If it exists, then an integration test crashed somewhere.")
                    .private_(false)
                    .create();
            // Ensure that repository exists
            for (int attempts = 0; attempts < 5; attempts++) {
                try {
                    repo = gitHubUtil.getRepo(repo.getFullName());
                    break;
                } catch (Exception e) {
                    log.info("Waiting for {} to be created", repo.getFullName());
                    Thread.sleep(TimeUnit.SECONDS.toMillis(1));
                }
            }

            repo.createContent("FROM " + image + ":test", "Integration Testing", "Dockerfile");
            createdRepos.add(repo);
            log.info("Initializing {}/{}", org.getLogin(), repoName);
            gitHubUtil.tryRetrievingContent(repo, "Dockerfile", repo.getDefaultBranch());
        }
    }

    public static void printCollectedExceptionsAndFail(List<Exception> exceptions, boolean exitWithFail) {
        for (int i = 0; i < exceptions.size(); i++) {
            log.error("Hit exception {}/{} while cleaning up.", i+1, exceptions.size());
            log.error("", exceptions.get(i));
        }
        if (exitWithFail && exceptions.size() > 0) {
            throw new RuntimeException(exceptions.get(0));
        }
    }

    public static void cleanAllRepos(List<GHRepository> createdRepos, boolean exitWithFail) throws Exception {
        List<Exception> exceptions = new ArrayList<>();
        exceptions.addAll(checkAndDelete(createdRepos));

        TestCommon.printCollectedExceptionsAndFail(exceptions, false);
    }

    private static Exception checkAndDelete(GHRepository repo) {
        log.info("deleting {}", repo.getFullName());
        try {
            repo.delete();
        } catch (Exception e) {
            return e;
        }
        return null;
    }

    private static List<Exception> checkAndDelete(List<GHRepository> repos) throws IOException {
        List<Exception> exceptions = new ArrayList<>();
        for (GHRepository repo : repos) {

            PagedIterable<GHRepository> forks;
            try {
                forks = repo.listForks();
                for (GHRepository fork : forks) {
                    Exception forkDeleteException = checkAndDelete(fork);
                    if (forkDeleteException != null) {
                        exceptions.add(forkDeleteException);
                    }
                }
            } catch (Exception getForksException) {
                log.error("Could not get forks for repo: ", repo.getFullName());
                exceptions.add(getForksException);
            }
            Exception repoDeleteException = checkAndDelete(repo);
            if (repoDeleteException != null) {
                exceptions.add(repoDeleteException);
            }
        }
        return exceptions;
    }

    public static void cleanBefore(List<String> repos, List<String> duplicatesCreatedByGithub,
                             String storeName, GitHub github) throws Exception {
        checkAndDeleteBefore(repos, storeName, github);
        checkAndDeleteBefore(duplicatesCreatedByGithub, storeName, github);
    }

    private static void checkAndDeleteBefore(List<String> repoNames, String storeName, GitHub github) throws IOException, InterruptedException {
        String user = github.getMyself().getLogin();
        for (String repoName : repoNames) {
            for (String org : ORGS) {
                checkAndDeleteBefore(Paths.get(user, repoName).toString(), github);
                checkAndDeleteBefore(Paths.get(org, repoName).toString(), github);
            }

        }
        checkAndDeleteBefore(Paths.get(user, storeName).toString(), github);
    }

    public static void checkAndDeleteBefore(String repoName, GitHub github) throws IOException, InterruptedException {
        GHRepository repo;
        try {
            repo = github.getRepository(repoName);
        } catch (FileNotFoundException fileNotFoundException) {
            return;
        }
        repo.delete();

        // Make sure the repo is actually deleted
        for (int attempts = 0; attempts < 60; attempts++) {
            try {
                github.getRepository(repoName);
            } catch (FileNotFoundException fileNotFoundException) {
                return;
            }
            log.info("Waiting for {} to fully delete...", repoName);
            Thread.sleep(TimeUnit.SECONDS.toMillis(1));
        }
        throw new FileNotFoundException(String.format("Unable to pre-delete repository %s during pre-test cleanup", repoName));
    }

    public static void addVersionStoreRepo(GitHub github, List<GHRepository> createdRepos, String storeName) throws IOException {
        String login = github.getMyself().getLogin();
        GHRepository storeRepo = github.getRepository(Paths.get(login, storeName).toString());
        createdRepos.add(storeRepo);
    }
}
