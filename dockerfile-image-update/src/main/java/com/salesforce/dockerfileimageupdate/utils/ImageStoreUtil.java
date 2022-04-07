package com.salesforce.dockerfileimageupdate.utils;

import com.salesforce.dockerfileimageupdate.storage.ImageStoreType;
import com.salesforce.dockerfileimageupdate.storage.ImageTagStore;
import com.salesforce.dockerfileimageupdate.storage.S3BackedImageTagStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Optional;

/**
 * ImageStore is the main entity we'll be using to initialize the image tag store
 * @author amukhopadhyay
 */
public class ImageStoreUtil {
    private static final Logger log = LoggerFactory.getLogger(PullRequests.class);
    private static ImageStoreType fromStoreUri(URI storeUri) {
        Optional<String> scheme = Optional.ofNullable(storeUri.getScheme());
        if (scheme.isPresent() && storeUri.getScheme().equals(S3BackedImageTagStore.s3Prefix))
            return ImageStoreType.S3;
        return ImageStoreType.GIT;
    }

    public static ImageTagStore initializeImageTagStore(DockerfileGitHubUtil dockerfileGitHubUtil, String store)
            throws Exception {
        URI storeUri = new URI(store);
        ImageStoreType imageStoreType = fromStoreUri(storeUri);
        ImageTagStore imageTagStore = null;
        switch (imageStoreType) {
            case S3:
                log.info("The underlying data store is S3.");
                imageTagStore = getStore(imageStoreType, storeUri);
                break;
            case GIT:
                log.info("The underlying data store is a Git repository.");
                imageTagStore = imageStoreType.getStore(dockerfileGitHubUtil, store);
                break;
            default:
                log.error("Unknown Image store type.");
        }
        return imageTagStore;
    }

    private static String getS3StoreName(URI storeUri) {
        return storeUri.getHost();
    }

    private static ImageTagStore getStore(ImageStoreType imageStoreType, URI storeUri) throws Exception{
        //DockerfileGitHubUtil is not required to initialize the S3 store
        return imageStoreType.getStore(null, getS3StoreName(storeUri));
    }

}
