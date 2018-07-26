/*
 * Copyright (c) 2017, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.subcommands.impl;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.salesforce.dockerfileimageupdate.subcommands.ExecutableWithNamespace;
import com.salesforce.dockerfileimageupdate.utils.Constants;
import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;
import com.salesforce.dockerfileimageupdate.SubCommand;
import net.sourceforge.argparse4j.inf.Namespace;
import org.kohsuke.github.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.*;

@SubCommand(help="updates all repositories' Dockerfiles",
        requiredParams = {Constants.STORE})
public class All implements ExecutableWithNamespace {
    private final static Logger log = LoggerFactory.getLogger(All.class);

    private DockerfileGitHubUtil dockerfileGitHubUtil;

    @Override
    public void execute(final Namespace ns, final DockerfileGitHubUtil dockerfileGitHubUtil) throws IOException, InterruptedException {
        loadDockerfileGithubUtil(dockerfileGitHubUtil);

        Map<String, String> imageToTagMap = new HashMap<>();
        Map<String, String> parentToImage = new HashMap<>();
        Map<String, String> parentToPath = new HashMap<>();

        Set<Map.Entry<String, JsonElement>> imageToTagStore = parseStoreToImagesMap(ns.get(Constants.STORE));
        for (Map.Entry<String, JsonElement> imageToTag : imageToTagStore) {
            String image = imageToTag.getKey();
            log.info("Repositories with image {} being forked.", image);
            imageToTagMap.put(image, imageToTag.getValue().getAsString());
            PagedSearchIterable<GHContent> contentsWithImage =
                    this.dockerfileGitHubUtil.findFilesWithImage(image, ns.get(Constants.GIT_ORG));
            forkRepositoriesFound(parentToPath, parentToImage, contentsWithImage, image);
        }

        GHMyself currentUser = this.dockerfileGitHubUtil.getMyself();
        if (currentUser == null) {
            throw new IOException("Could not retrieve authenticated user.");
        }

        log.info("Retrieving all the forks...");
        PagedIterable<GHRepository> repos = dockerfileGitHubUtil.getGHRepositories(parentToPath, currentUser);

        String message = ns.get("m");
        List<IOException> exceptions = new ArrayList<>();
        for (GHRepository repo : repos) {
            try {
                changeDockerfiles(parentToPath, parentToImage, imageToTagMap, repo, message, ns.get("c"));
            } catch (IOException e) {
                exceptions.add(e);
            }
        }

        if (exceptions.size() > 0) {
            log.info("There were {} errors with changing Dockerfiles.", exceptions.size());
            throw exceptions.get(0);
        }
    }

    protected void loadDockerfileGithubUtil(DockerfileGitHubUtil _dockerfileGitHubUtil) {
        dockerfileGitHubUtil = _dockerfileGitHubUtil;
    }

    protected void forkRepositoriesFound(Map<String, String> parentToPath,
                                         Map<String, String> parentToImage,
                                         PagedSearchIterable<GHContent> contentsWithImage, String image) throws IOException {
        log.info("Forking {} repositories...", contentsWithImage.getTotalCount());
        for (GHContent c : contentsWithImage) {
            /* Kohsuke's GitHub API library, when retrieving the forked repository, looks at the name of the parent to
             * retrieve. The issue with that is: GitHub, when forking two or more repositories with the same name,
             * automatically fixes the names to be unique (by appending "-#" to the end). Because of this edge case, we
             * cannot save the forks and iterate over the repositories; else, we end up missing/not updating the
             * repositories that were automatically fixed by GitHub. Instead, we save the names of the parent repos
             * in the map above, find the list of repositories under the authorized user, and iterate through that list.
             */
            GHRepository parent = c.getOwner();
            log.info("Forking {}...", parent.getFullName());
            parentToPath.put(c.getOwner().getFullName(), c.getPath());
            parentToImage.put(c.getOwner().getFullName(), image);
            dockerfileGitHubUtil.checkFromParentAndFork(parent);
        }
    }

    protected Set<Map.Entry<String, JsonElement>> parseStoreToImagesMap(String storeName)
            throws IOException, InterruptedException {
        GHMyself myself = dockerfileGitHubUtil.getMyself();
        String login = myself.getLogin();
        GHRepository store = dockerfileGitHubUtil.getRepo(Paths.get(login, storeName).toString());

        GHContent storeContent = dockerfileGitHubUtil.tryRetrievingContent(store, Constants.STORE_JSON_FILE,
                store.getDefaultBranch());

        if (storeContent == null) {
            return null;
        }

        JsonElement json;
        try (InputStream stream = storeContent.read(); InputStreamReader streamR = new InputStreamReader(stream)) {
            try {
                json = new JsonParser().parse(streamR);
            } catch (JsonParseException e) {
                log.warn("Not a JSON format store.");
                return null;
            }
        }

        JsonElement imagesJson = json.getAsJsonObject().get("images");
        return imagesJson.getAsJsonObject().entrySet();
    }

    protected void changeDockerfiles(Map<String, String> parentToPath,
                                     Map<String, String> parentToImage,
                                     Map<String, String> imageToTagMap,
                                     GHRepository initialRepo, String message, String commitMessage) throws IOException, InterruptedException {
        /* The Github API does not provide the parent if retrieved through a list. If we want to access its parent,
         * we need to retrieve it once again.
         */
        GHRepository retrievedRepo;
        if (initialRepo.isFork()) {
            retrievedRepo = dockerfileGitHubUtil.getRepo(initialRepo.getFullName());
        } else {
            return;
        }
        GHRepository parent = retrievedRepo.getParent();

        if (parent == null || !parentToPath.containsKey(parent.getFullName())) {
            return;
        }
        log.info("Fixing Dockerfiles in {}...", initialRepo.getFullName());
        String parentName = parent.getFullName();
        String image = parentToImage.get(parentName);
        String tag = imageToTagMap.get(image);
        String branch = retrievedRepo.getDefaultBranch();
        GHContent content = dockerfileGitHubUtil.tryRetrievingContent(retrievedRepo, parentToPath.get(parentName),
                branch);
        dockerfileGitHubUtil.modifyOnGithub(content, branch, image, tag, commitMessage);
        dockerfileGitHubUtil.createPullReq(parent, branch, retrievedRepo, message);
    }



}
