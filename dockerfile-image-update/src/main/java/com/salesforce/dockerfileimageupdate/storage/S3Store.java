package com.salesforce.dockerfileimageupdate.storage;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

public class S3Store implements ImageTagStore {
    private static final Logger log = LoggerFactory.getLogger(S3Store.class);
    private static final Character S3_FILE_KEY_PATH_DELIMITER = ';';
    private final AmazonS3 s3;
    private final String store;

    public S3Store(AmazonS3 s3, String store) {
        this.s3 = s3;
        this.store = store;
    }

    /* The store link should be a bucket name on S3. */
    public void updateStore(String img, String tag) throws IOException {
        if (store == null) {
            log.info("Image tag store cannot be null. Skipping store update...");
            return;
        }
        log.info("Updating store: {} with image: {} tag: {}...", store, img, tag);
        if (!s3.doesBucketExistV2(store)) {
            throw new IOException(String.format("The S3 bucket with name %s does not exist. Cannot proceed.", store));
        }
        String key = convertImageStringToS3ObjectKey(img);
        s3.putObject(store, key, tag);
    }

    public HashMap<String, String> getStoreContent(DockerfileGitHubUtil dockerfileGitHubUtil, String storeName) {
        HashMap<String, String> imageNameWithTag = new HashMap<>();
        ListObjectsV2Result result = getS3Objects();
        List<S3ObjectSummary> objects = result.getObjectSummaries();
        for (S3ObjectSummary os : objects) {
            String key = os.getKey();
            String image = convertS3ObjectKeyToImageString(key);
            S3Object o = getS3Object(store, key);
            String tag = o.getObjectContent().toString();
            imageNameWithTag.put(image, tag);
        }
        return imageNameWithTag;
    }

    protected String convertImageStringToS3ObjectKey(String img) {
        return img.replace('/', S3_FILE_KEY_PATH_DELIMITER);
    }

    protected String convertS3ObjectKeyToImageString(String key) {
        return key.replace(S3_FILE_KEY_PATH_DELIMITER, '/');
    }

    protected ListObjectsV2Result getS3Objects() {
        return s3.listObjectsV2(store);
    }

    protected S3Object getS3Object(String store, String key) {
        return s3.getObject(store, key);
    }
}
