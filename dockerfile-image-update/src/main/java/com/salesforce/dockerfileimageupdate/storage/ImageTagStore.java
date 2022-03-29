package com.salesforce.dockerfileimageupdate.storage;

import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;

import java.io.IOException;
import java.util.HashMap;

public interface ImageTagStore {
    void updateStore(String img, String tag) throws IOException;
    HashMap<String, String> getStoreContent(DockerfileGitHubUtil dockerfileGitHubUtil, String storeName) throws IOException, InterruptedException;
}
