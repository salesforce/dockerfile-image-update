/*
 * Copyright (c) 2017, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.itest.tests;

import com.salesforce.dockerfileimageupdate.githubutils.GithubUtil;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by afalko on 10/19/17.
 */
public class TestCommon {
    private static final Logger log = LoggerFactory.getLogger(TestCommon.class);

    public static final List<String> ORGS = Arrays.asList(
            "dockerfile-image-update-itest", "dockerfile-image-update-itest-2", "dockerfile-image-update-itest-3");

    public static void initializeRepos(GHOrganization org, List<String> repos, String image,
                                       List<GHRepository> createdRepos, GithubUtil githubUtil) throws Exception {
        for (String s : repos) {
            GHRepository repo = org.createRepository(s)
                    .description("Delete if this exists. If it exists, then an integration test crashed somewhere.")
                    .private_(false)
                    .create();
            repo.createContent("FROM " + image + ":test", "Integration Testing", "Dockerfile");
            createdRepos.add(repo);
            log.info("Initializing {}/{}", org.getLogin(), s);
            githubUtil.tryRetrievingContent(repo, "Dockerfile", repo.getDefaultBranch());
        }
    }

    public static void printCollectedExceptionsAndFail(List<Exception> exceptions) {
        for (int i = 0; i < exceptions.size(); i++) {
            log.error("Hit exception {}/{} while cleaning up.", i+1, exceptions.size());
            log.error("", exceptions.get(i));
        }
        if (exceptions.size() > 0) {
            throw new RuntimeException(exceptions.get(0));
        }
    }

    public static void cleanAllRepos(GitHub github, String versionStoreName, List<GHRepository> createdRepos) throws Exception {
        List<Exception> exceptions = new ArrayList<>();
        exceptions.addAll(checkAndDelete(createdRepos));
        String login = github.getMyself().getLogin();
        GHRepository storeRepo = github.getRepository(Paths.get(login, versionStoreName).toString());
        exceptions.add(checkAndDelete(storeRepo));

        TestCommon.printCollectedExceptionsAndFail(exceptions);
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
            for (GHRepository fork : repo.listForks()) {
                Exception e1 = checkAndDelete(fork);
                if (e1 != null) {
                    exceptions.add(e1);
                }
            }
            Exception e = checkAndDelete(repo);
            if (e != null) {
                exceptions.add(e);
            }
        }
        return exceptions;
    }
}
