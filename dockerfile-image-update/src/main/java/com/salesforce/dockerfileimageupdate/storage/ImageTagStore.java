package com.salesforce.dockerfileimageupdate.storage;

import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;

import java.io.IOException;
import java.util.List;

/**
 * This is an interface for the image tag store. The underlying image tag store can be a Git repo or an S3 bucket.
 */

public interface ImageTagStore {

    /**
     * This method updates the image tag store by updating the image version for the image name passed.
     *
     * @param img the name of the image that needs to be updated.
     * @param tag the version of the image that it needs to update to.
     * @throws IOException when fails to update tag store
     */
    void updateStore(String img, String tag) throws IOException;

    /**
     * This method gets the content of the image tag store.
     *
     * @param dockerfileGitHubUtil the dockerfileGitHubUtil object that is used to interact with an underlying Git repo.
     * @param storeName the name of the store whose content needs to be fetched.
     * @return A Map of image name to image version.
     * @throws IOException when fails to get the tag store content
     * @throws InterruptedException when interrupted while getting the tag store content
     */

    List<ImageTagStoreContent> getStoreContent(DockerfileGitHubUtil dockerfileGitHubUtil, String storeName) throws IOException, InterruptedException;
}
