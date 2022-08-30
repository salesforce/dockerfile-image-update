package com.salesforce.dockerfileimageupdate.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.salesforce.dockerfileimageupdate.utils.Constants;
import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;
import com.salesforce.dockerfileimageupdate.utils.GitHubUtil;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.kohsuke.github.GHBlob;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHMyself;
import org.kohsuke.github.GHRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitHubJsonStore implements ImageTagStore {
    private static final Logger log = LoggerFactory.getLogger(GitHubJsonStore.class);
    private final GitHubUtil gitHubUtil;
    private final String store;

    public GitHubJsonStore(GitHubUtil gitHubUtil, String store) {
        this.gitHubUtil = gitHubUtil;
        this.store = store;
    }

    /* The store link should be a repository name on Github. */
    public void updateStore(String img, String tag) throws IOException {
        if (store == null) {
            log.info("Image tag store cannot be null. Skipping store update...");
            return;
        }
        log.info("Updating store: {} with image: {} tag: {}...", store, img, tag);
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

    /**
     * This methods will check if the tag store at {@param path}
     * is created or not, if not it will create a new tag store
     * @param repo {@link GHRepository} git repository object
     * @param path filename with path with in repo for the tag store
     * @throws IOException when tag store at {@param path}
     * can not be created
     */
    private void initializeTagStoreIfRequired(GHRepository repo, String path) throws IOException {
        try {
            repo.getFileContent(path);
        } catch (FileNotFoundException e) {
            log.info("Image tag store {} not found. Creating a new image tag store",
                    path);
            repo.createContent().content("").message("initializing store").path(path).commit();
        } catch (IOException ex) {
            if (ex.getMessage().contains("too_large")) {
                log.info("Image tag store {} is already initialized and has size more than 1 MB",
                        path);
            } else {
                throw ex;
            }
        }
    }

    protected void updateStoreOnGithub(GHRepository repo, String path, String img, String tag) throws IOException {
        initializeTagStoreIfRequired(repo, path);

        GHCommit commit = repo.getCommit(repo.getDefaultBranch());
        GHBlob blob = commit.getTree().getEntry(path).asBlob();
        JsonElement json = null;

        String text;
        try (InputStream stream = blob.read()) {
            text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            json = JsonParser.parseString(text);
        } catch (JsonParseException e) {
            log.warn("Not a JSON formatted store. Clearing and rewriting as JSON...");
            json = JsonNull.INSTANCE;
        }

        String jsonOutput = getAndModifyJsonString(json, img, tag);

        String treeSha = repo
                .createTree()
                .baseTree(commit.getSHA1())
                .add(path, jsonOutput, false).create().getSha();

        String commitSha = repo.createCommit()
                .message(String.format("Updated image %s with tag %s.%n@rev none@", img, tag))
                .tree(treeSha)
                .parent(commit.getSHA1())
                .create()
                .getSHA1();
        repo.getRef("heads/" + repo.getDefaultBranch()).updateTo(commitSha);
    }

    protected String getAndModifyJsonString(JsonElement json, String img, String tag) {
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

    public Set<Map.Entry<String, JsonElement>> parseStoreToImagesMap(DockerfileGitHubUtil dockerfileGitHubUtil, String storeName)
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
                json = JsonParser.parseReader(streamR);
            } catch (JsonParseException e) {
                log.warn("Not a JSON format store.");
                return Collections.emptySet();
            }
        }

        JsonElement imagesJson = json.getAsJsonObject().get("images");
        return imagesJson.getAsJsonObject().entrySet();
    }

    public List<ImageTagStoreContent> getStoreContent(DockerfileGitHubUtil dockerfileGitHubUtil, String storeName) throws IOException, InterruptedException {
        Set<Map.Entry<String, JsonElement>> imageToTagStore = parseStoreToImagesMap(dockerfileGitHubUtil, storeName);
        return imageToTagStore.stream()
                .map(entry -> new ImageTagStoreContent(entry.getKey(), entry.getValue().getAsString()))
                .collect(Collectors.toList());
    }
}
