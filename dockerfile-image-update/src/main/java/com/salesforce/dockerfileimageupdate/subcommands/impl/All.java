/*
 * Copyright (c) 2017, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.subcommands.impl;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.*;

@SubCommand(help="updates all repositories' Dockerfiles",
        requiredParams = {Constants.STORE})
public class All implements ExecutableWithNamespace {
    private static final Logger log = LoggerFactory.getLogger(All.class);

    private DockerfileGitHubUtil dockerfileGitHubUtil;

    @Override
    public void execute(final Namespace ns, final DockerfileGitHubUtil dockerfileGitHubUtil) throws IOException, InterruptedException {
        loadDockerfileGithubUtil(dockerfileGitHubUtil);

        Map<String, String> imageToTagMap = new HashMap<>();
        Multimap<String, String> imagesFoundInParentRepo = ArrayListMultimap.create();
        Multimap<String, String> pathToDockerfilesInParentRepo = ArrayListMultimap.create();

        Set<Map.Entry<String, JsonElement>> imageToTagStore = parseStoreToImagesMap(ns.get(Constants.STORE));
        for (Map.Entry<String, JsonElement> imageToTag : imageToTagStore) {
            String image = imageToTag.getKey();
            log.info("Repositories with image {} being forked.", image);
            imageToTagMap.put(image, imageToTag.getValue().getAsString());
            PagedSearchIterable<GHContent> contentsWithImage =
                    this.dockerfileGitHubUtil.findFilesWithImage(image, ns.get("o"));
            forkRepositoriesFound(pathToDockerfilesInParentRepo,
                    imagesFoundInParentRepo, contentsWithImage, image);
        }

        log.info("imageToTagMap: {}", imageToTagMap.toString());

        GHMyself currentUser = this.dockerfileGitHubUtil.getMyself();
        if (currentUser == null) {
            throw new IOException("Could not retrieve authenticated user.");
        }

        log.info("Retrieving all the forks...");
        PagedIterable<GHRepository> listOfcurrUserRepos =
                dockerfileGitHubUtil.getGHRepositories(pathToDockerfilesInParentRepo, currentUser);

        List<IOException> exceptions = new ArrayList<>();
        List<String> skippedRepos = new ArrayList<>();
//        log.info("all imageToTagMap: {}", imageToTagMap.toString());
//        log.info("all imagesFoundInParentRepo: {}", imagesFoundInParentRepo.toString());
//        log.info("all pathToDockerfilesInParentRepo: {}", pathToDockerfilesInParentRepo.toString());
        for (GHRepository currUserRepo : listOfcurrUserRepos) {
            try {
                changeDockerfiles(ns, pathToDockerfilesInParentRepo, imagesFoundInParentRepo, imageToTagMap, currUserRepo,
                        skippedRepos);
            } catch (IOException e) {
                exceptions.add(e);
            }
        }

        if (!exceptions.isEmpty()) {
            log.info("There were {} errors with changing Dockerfiles.", exceptions.size());
            throw exceptions.get(0);
        }

        if (!skippedRepos.isEmpty()) {
            log.info("List of repos skipped: {}", skippedRepos.toArray());
        }
    }

    protected void loadDockerfileGithubUtil(DockerfileGitHubUtil _dockerfileGitHubUtil) {
        dockerfileGitHubUtil = _dockerfileGitHubUtil;
    }

    protected void forkRepositoriesFound(Multimap<String, String> pathToDockerfilesInParentRepo,
                                         Multimap<String, String> imagesFoundInParentRepo,
                                         PagedSearchIterable<GHContent> contentsWithImage,
                                         String image) throws IOException {
        log.info("Forking {} repositories...", contentsWithImage.getTotalCount());
        List<String> parentReposForked = new ArrayList<>();
        GHRepository parent;
        String parentRepoName = null;
        for (GHContent c : contentsWithImage) {
            /* Kohsuke's GitHub API library, when retrieving the forked repository, looks at the name of the parent to
             * retrieve. The issue with that is: GitHub, when forking two or more repositories with the same name,
             * automatically fixes the names to be unique (by appending "-#" to the end). Because of this edge case, we
             * cannot save the forks and iterate over the repositories; else, we end up missing/not updating the
             * repositories that were automatically fixed by GitHub. Instead, we save the names of the parent repos
             * in the map above, find the list of repositories under the authorized user, and iterate through that list.
             */
            parent = c.getOwner();
            parentRepoName = parent.getFullName();
            if (parent.isFork()) {
                log.warn("Skipping {} because it's a fork already. Sending a PR to a fork is unsupported at the moment.",
                        parentRepoName);
            } else {
                pathToDockerfilesInParentRepo.put(c.getOwner().getFullName(), c.getPath());
                imagesFoundInParentRepo.put(c.getOwner().getFullName(), image);

                // fork the parent if not already forked
                if (!parentReposForked.contains(parentRepoName)) {
                    log.info("Forking repo: {}", parentRepoName);
                    dockerfileGitHubUtil.closeOutdatedPullRequestAndForkParent(parent);
                    parentReposForked.add(parentRepoName);
                }
            }
        }

        log.info("Path to Dockerfiles in repo '{}': {}", parentRepoName, pathToDockerfilesInParentRepo.toString());
        log.info("All images found in repo '{}': {}", parentRepoName, imagesFoundInParentRepo.toString());
    }

    protected Set<Map.Entry<String, JsonElement>> parseStoreToImagesMap(String storeName)
            throws IOException, InterruptedException {
        GHMyself myself = dockerfileGitHubUtil.getMyself();
        String login = myself.getLogin();
        GHRepository store = dockerfileGitHubUtil.getRepo(Paths.get(login, storeName).toString());

        GHContent storeContent = dockerfileGitHubUtil.tryRetrievingContent(store, Constants.STORE_JSON_FILE,
                store.getDefaultBranch());

        if (storeContent == null) {
            return Collections.emptySet();
        }

        JsonElement json;
        try (InputStream stream = storeContent.read(); InputStreamReader streamR = new InputStreamReader(stream)) {
            try {
                json = new JsonParser().parse(streamR);
            } catch (JsonParseException e) {
                log.warn("Not a JSON format store.");
                return Collections.emptySet();
            }
        }

        JsonElement imagesJson = json.getAsJsonObject().get("images");
        return imagesJson.getAsJsonObject().entrySet();
    }

    protected void changeDockerfiles(Namespace ns,
                                     Multimap<String, String> pathToDockerfilesInParentRepo,
                                     Multimap<String, String> imagesFoundInParentRepo,
                                     Map<String, String> imageToTagMap,
                                     GHRepository currUserRepo,
                                     List<String> skippedRepos) throws IOException, InterruptedException {
        /* The Github API does not provide the parent if retrieved through a list. If we want to access its parent,
         * we need to retrieve it once again.
         */
        GHRepository forkedRepo;
        if (currUserRepo.isFork()) {
            try {
                forkedRepo = dockerfileGitHubUtil.getRepo(currUserRepo.getFullName());
            } catch (FileNotFoundException e) {
                /* The edge case here: If a different command calls getGHRepositories, and then this command calls
                 * it again within 60 seconds, it will still have the same list of repositories (because of caching).
                 * However, between the previous and current call, if some of those repositories are deleted, the call
                 * above may cause a FileNotFoundException. This clause prevents that exception from stopping our call;
                 * we do not need to stop because getGHRepositories checks that we have all the repositories we need.
                 *
                 * The integration test calls the testParent -> testAllCommand -> testIdempotency, and the
                 * testIdempotency was failing because of this edge condition.
                 */

                log.warn("This repository does not exist. The list of repositories must be outdated, but the list" +
                        "contains the repositories we need, so we ignore this error.");
                return;
            }
        } else {
            return;
        }
        GHRepository parent = forkedRepo.getParent();

        if (parent == null || !pathToDockerfilesInParentRepo.containsKey(parent.getFullName())) {
            return;
        }

        log.info("Fixing Dockerfiles in {}...", forkedRepo.getFullName());
        String parentName = parent.getFullName();
        String branch = (ns.get("b") == null) ? forkedRepo.getDefaultBranch() : ns.get("b");

        String pathToDockerfile;
        String image;
        String tag;
        GHContent content;
        boolean isContentModified = false;
        boolean isRepoSkipped = true;

        Iterator<String> pathToDockerfileInParentRepoIterator = pathToDockerfilesInParentRepo.get(parentName).iterator();
        Iterator<String> imagesFoundInParentRepoIterator = imagesFoundInParentRepo.get(parentName).iterator();

        while (pathToDockerfileInParentRepoIterator.hasNext() && imagesFoundInParentRepoIterator.hasNext()) {
            pathToDockerfile = pathToDockerfileInParentRepoIterator.next();
            image = imagesFoundInParentRepoIterator.next();
            tag = imageToTagMap.get(image);
            log.info("pathToDockerfile: {} , image: {}, tag: {}", pathToDockerfile, image, tag);
            content = dockerfileGitHubUtil.tryRetrievingContent(forkedRepo, pathToDockerfile, branch);
            log.info("content: {}", content);
            if (content != null) {
                dockerfileGitHubUtil.modifyOnGithub(content, branch, image, tag, ns.get("c"));
                isContentModified = true;
                isRepoSkipped = false;
            } else {
                log.info("No Dockerfile found at path: '{}'", pathToDockerfile);
            }
        }

        if (isRepoSkipped) {
            log.info("Skipping repo '{}' because contents of it's fork could not be retrieved. Moving ahead...",
                    parentName);
            skippedRepos.add(forkedRepo.getFullName());
        }

        if (isContentModified) {
            dockerfileGitHubUtil.createPullReq(parent, branch, forkedRepo, ns.get("m"));
        }
    }
}
