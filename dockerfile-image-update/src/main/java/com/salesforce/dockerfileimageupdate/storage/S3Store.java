package com.salesforce.dockerfileimageupdate.storage;

import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.google.gson.*;
import com.salesforce.dockerfileimageupdate.utils.Constants;
import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;
import com.salesforce.dockerfileimageupdate.utils.S3BucketUtil;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHMyself;
import org.kohsuke.github.GHRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class S3Store {
    private static final Logger log = LoggerFactory.getLogger(S3Store.class);
    private  final S3BucketUtil s3BucketUtil;
    private final String store;

    public S3Store(S3BucketUtil s3BucketUtil, String store) {
        this.s3BucketUtil = s3BucketUtil;
        this.store = store;
    }

    /* The store link should be a bucket name on S3. */
    public void updateS3Store(String img, String tag) throws IOException {
        if (store == null) {
            log.info("Image tag store cannot be null. Skipping store update...");
            return;
        }
        log.info("Updating store: {} with image: {} tag: {}...", store, img, tag);
        if (!s3BucketUtil.getS3Client().doesBucketExistV2(store)) {
            log.error("The S3 bucket with name {} does not exist. Cannot proceed.", store);
        }
        String key = convertImageStringToS3ObjectKey(img);
        s3BucketUtil.getS3Client().putObject(store, key, tag);
    }

    protected String convertImageStringToS3ObjectKey(String img) {
        return img.replace('/', ';');
    }

    public String convertS3ObjectKeyToImageString(String key) {
        return key.replace(';', '/');
    }

    public ListObjectsV2Result getS3Objects() {
        return s3BucketUtil.getS3Client().listObjectsV2(store);
    }
}
