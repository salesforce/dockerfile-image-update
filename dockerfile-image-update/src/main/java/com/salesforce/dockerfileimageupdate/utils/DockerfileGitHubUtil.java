/*
 * Copyright (c) 2017, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.utils;

import com.google.common.collect.Multimap;
import com.google.gson.*;
import com.salesforce.dockerfileimageupdate.subcommands.impl.Child;
import org.kohsuke.github.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Paths;
import java.util.List;

/**
 * Created by minho.park on 7/22/16.
 */
public class DockerfileGitHubUtil {
    private static final Logger log = LoggerFactory.getLogger(Child.class);
    private final GitHubUtil gitHubUtil;

    public DockerfileGitHubUtil(GitHubUtil gitHubUtil) {
        this.gitHubUtil = gitHubUtil;
    }

    protected GitHubUtil getGitHubUtil() { return gitHubUtil; }

    public GHRepository closeOutdatedPullRequestAndForkParent(GHRepository parent) throws IOException {

        for (GHRepository fork : parent.listForks()) {
            String forkOwner = fork.getOwnerName();
            GHMyself myself = gitHubUtil.getMyself();
            String myselfLogin = myself.getLogin();
            if (forkOwner.equals(myselfLogin)) {
                GHPullRequest pr = getPullRequestWithPullReqIdentifier(parent);
                log.info("pr: {}", pr);
                // Only reason we close the existing PR, delete fork and re-fork, is because there is no way to
                // determine if the existing fork is compatible with it's parent.
                if (pr != null) {
                    // close the pull-request since the fork is out of date
                    log.info("closing existing pr: {}", pr.getUrl());
                    pr.close();
                }
                // delete fork if one already exists before re-forking
                gitHubUtil.safeDeleteRepo(fork);
            }
        }
        return gitHubUtil.createFork(parent);
    }

    public GHMyself getMyself() throws IOException {
        return gitHubUtil.getMyself();
    }

    public GHRepository getRepo(String repoName) throws IOException {
        return gitHubUtil.getRepo(repoName);
    }

    public PagedSearchIterable<GHContent> findFilesWithImage(String query, String org) throws IOException {
        GHContentSearchBuilder search = gitHubUtil.startSearch();
        search.language("Dockerfile");
        if (org != null) {
            search.user(org);
        }
        if (query.substring(query.lastIndexOf(' ') + 1).length() <= 1) {
            throw new IOException("Invalid image name.");
        }
        search.q("\"FROM " + query + "\"");
        log.info("Searching for {}", query);
        PagedSearchIterable<GHContent> files = search.list();
        int totalCount = files.getTotalCount();
        log.info("Number of files found for {}:{}", query, totalCount);
        return files;
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
        return gitHubUtil.getGHRepositories(pathToDockerfileInParentRepo, currentUser);
    }

    public void modifyAllOnGithub(GHRepository repo, String branch,
                                  String img, String tag) throws IOException, InterruptedException {
        List<GHContent> tree = null;

        /* There are issues with the GitHub API returning that the GitHub repository exists, but has no content,
         * when we try to pull on it the moment it is created. The system must wait a short time before we can move on.
         */
        for (int i = 0; i < 5; i++) {
            try {
                tree = repo.getDirectoryContent(".", branch);
                break;
            } catch (FileNotFoundException e1) {
                log.warn("Content in repository not created yet. Retrying connection to fork...");
                Thread.sleep(1000);
            }
        }
        for (GHContent con : tree) {
            modifyOnGithubRecursive(repo, con, branch, img, tag);
        }
    }

    protected void modifyOnGithubRecursive(GHRepository repo, GHContent content,
                                           String branch, String img, String tag) throws IOException {
        if (content.isFile()) {
            modifyOnGithub(content, branch, img, tag, "");
        } else {
            for (GHContent newContent : repo.getDirectoryContent(content.getPath(), branch)) {
                modifyOnGithubRecursive(repo, newContent, branch, img, tag);
            }
        }
    }

    public GHContent tryRetrievingContent(GHRepository repo, String path, String branch) throws InterruptedException {
        return gitHubUtil.tryRetrievingContent(repo, path, branch);
    }

    public void modifyOnGithub(GHContent content,
                               String branch, String img, String tag, String customMessage) throws IOException {
        try (InputStream stream = content.read();
             InputStreamReader streamR = new InputStreamReader(stream);
             BufferedReader reader = new BufferedReader(streamR)) {
            findImagesAndFix(content, branch, img, tag, customMessage, reader);
        }
    }

    protected void findImagesAndFix(GHContent content,
                                    String branch, String img, String tag, String customMessage,
                                    BufferedReader reader) throws IOException {
        StringBuilder strB = new StringBuilder();
        boolean modified = rewriteDockerfile(img, tag, reader, strB);
        log.info("content: {}", content.getPath());
        if (modified) {
            log.info("modified: {}", content.getPath());
            content.update(strB.toString(),
                    "Fixed Dockerfile base image in /" + content.getPath() + ".\n" + customMessage, branch);
        }
    }

    protected boolean rewriteDockerfile(String img, String tag, BufferedReader reader, StringBuilder strB) throws IOException {
        String line;
        boolean modified = false;
        while ( (line = reader.readLine()) != null ) {


            /* Once true, should stay true. */
            modified = changeIfDockerfileBaseImageLine(img, tag, strB, line) || modified;
        }
        return modified;
    }

    protected boolean changeIfDockerfileBaseImageLine(String img, String tag, StringBuilder strB, String line) {
        String trimmedLine = line.trim();
        int indexOfTag = line.lastIndexOf(':');
        if (indexOfTag < 0) {
            indexOfTag = 0;
        }
        String lineWithoutTag = line.substring(0, indexOfTag);
        boolean modified = false;
        boolean isExactImage = trimmedLine.endsWith(img) || lineWithoutTag.endsWith(img);

        if (line.contains(Constants.BASE_IMAGE_INST) && isExactImage) {
            strB.append(Constants.BASE_IMAGE_INST).append(" ").append(img).append(":").append(tag).append("\n");
            if (!line.substring(line.lastIndexOf(':') + 1).equals(tag)) {
                modified = true;
            }
        } else {
            strB.append(line).append("\n");
        }
        return modified;
    }

    /* The store link should be a repository name on Github. */
    public void updateStore(String store, String img, String tag) throws IOException {
        if (store == null) {
            log.info("Image tag store cannot be null. Skipping store update...");
            return;
        }
        GHRepository storeRepo;
        try {
            GHMyself myself = gitHubUtil.getMyself();
            String ownerOrg = myself.getLogin();
            storeRepo = gitHubUtil.getRepo(Paths.get(ownerOrg, store).toString());
        } catch (IOException e) {
            storeRepo = gitHubUtil.createPublicRepo(store);
        }
        updateStoreOnGithub(storeRepo, Constants.STORE_JSON_FILE, img, tag);
    }

    protected void updateStoreOnGithub(GHRepository repo, String path, String img, String tag) throws IOException {
        try {
            repo.getFileContent(path);
        } catch (IOException e) {
            repo.createContent("", "initializing store", path);
        }

        String latestCommit = repo.getBranches().get(repo.getDefaultBranch()).getSHA1();
        log.info("Loading image store at commit {}", latestCommit);
        GHContent content = repo.getFileContent(path, latestCommit);

        if (content.isFile()) {
            JsonElement json;
            try (InputStream stream = content.read(); InputStreamReader streamR = new InputStreamReader(stream)) {
                try {
                    json = new JsonParser().parse(streamR);
                } catch (JsonParseException e) {
                    log.warn("Not a JSON format store. Clearing and rewriting as JSON...");
                    json = JsonNull.INSTANCE;
                }
            }
            String jsonOutput = getAndModifyJsonString(json, img, tag);
            content.update(jsonOutput,
                    String.format("Updated image %s with tag %s.\n@rev none@", img, tag), repo.getDefaultBranch());
        }
    }

    protected String getAndModifyJsonString(JsonElement json, String img, String tag) throws IOException {
        JsonElement images;
        if (json.isJsonNull()) {
            json = new JsonObject();
            images = new JsonObject();
            json.getAsJsonObject().add("images", images);
        }
        images = json.getAsJsonObject().get("images");
        if (images == null) {
            images = new JsonObject();
            json.getAsJsonObject().add("images", images);
            images = json.getAsJsonObject().get("images");
        }
        JsonElement newTag = new JsonPrimitive(tag);
        images.getAsJsonObject().add(img, newTag);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(json);
    }

    public void createPullReq(GHRepository origRepo,
                              String branch, GHRepository forkRepo,
                              String message) throws InterruptedException, IOException {
        if (message == null) {
            message = "Automatic Dockerfile Image Updater";
        }
        while (true) {
            int pullRequestExitCode = gitHubUtil.createPullReq(origRepo, branch, forkRepo, message, Constants.PULL_REQ_ID);
            if (pullRequestExitCode == 0) {
                return;
            } else if (pullRequestExitCode == 1) {
                gitHubUtil.safeDeleteRepo(forkRepo);
                return;
            }
        }
    }

    private GHPullRequest getPullRequestWithPullReqIdentifier(GHRepository parent) throws IOException {
        List<GHPullRequest> pullRequests;
        GHUser myself;
        try {
            pullRequests = parent.getPullRequests(GHIssueState.OPEN);
            myself = gitHubUtil.getMyself();
        } catch (IOException e) {
            log.warn("Error occurred while retrieving pull requests for {}", parent.getFullName());
            return null;
        }

        for (GHPullRequest pullRequest : pullRequests) {
            GHUser user = pullRequest.getHead().getUser();
            if (myself.equals(user) && pullRequest.getBody().equals(Constants.PULL_REQ_ID)) {
                return pullRequest;
            }
        }
        return null;
    }
}
