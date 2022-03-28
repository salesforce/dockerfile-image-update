package com.salesforce.dockerfileimageupdate.storage;

import com.google.gson.JsonElement;
import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

public interface ImageTagStore {
    void updateStore(String img, String tag) throws IOException;

    Set<Map.Entry<String, JsonElement>> parseStoreToImagesMap(DockerfileGitHubUtil dockerfileGitHubUtil, String storeName)
            throws IOException, InterruptedException;
}
