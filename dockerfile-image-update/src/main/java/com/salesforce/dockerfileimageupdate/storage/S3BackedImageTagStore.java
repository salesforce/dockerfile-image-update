package com.salesforce.dockerfileimageupdate.storage;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * S3BackedImageTagStore is the main entity we'll be using to interact with the image tag store stored in S3
 * @author amukhopadhyay
 */
public class S3BackedImageTagStore implements ImageTagStore {
    private static final Logger log = LoggerFactory.getLogger(S3BackedImageTagStore.class);
    private static final Character S3_FILE_KEY_PATH_DELIMITER = '.';
    public static final String s3Prefix = "s3";
    private final AmazonS3 s3;
    private final String store;

    public S3BackedImageTagStore(AmazonS3 s3, @NonNull String store) {
        this.s3 = s3;
        this.store = store;
    }

    /* The store link should be a bucket name on S3. */
    public void updateStore(String img, String tag) throws IOException {
        log.info("Updating store: {} with image: {} tag: {}...", store, img, tag);
        if (s3.doesBucketExistV2(store)) {
            String key = convertImageStringToS3ObjectKey(img);
            s3.putObject(store, key, tag);
        } else {
            throw new IOException(String.format("The S3 bucket with name %s does not exist. Cannot proceed.", store));
        }

    }

    public List<ImageTagStoreContent> getStoreContent(DockerfileGitHubUtil dockerfileGitHubUtil, String storeName) throws InterruptedException {
        List<ImageTagStoreContent> imageNamesWithTag;
        Map<String, Date> imageNameWithAccessTime = new HashMap<>();
        ListObjectsV2Result result = getS3Objects();
        List<S3ObjectSummary> objects = result.getObjectSummaries();
        for (S3ObjectSummary os : objects) {
            Date lastModified = os.getLastModified();
            String key = os.getKey();
            imageNameWithAccessTime.put(key, lastModified);
        }
        imageNamesWithTag = getStoreContentSortedByAccessDate(imageNameWithAccessTime);
        return imageNamesWithTag;
    }

    protected List<ImageTagStoreContent> getStoreContentSortedByAccessDate(Map<String, Date> imageNameWithAccessTime) throws InterruptedException {
        List<ImageTagStoreContent> imageNameWithTagSortedByAccessDate = new ArrayList<>();
        Map<String, Date> sortedResult = imageNameWithAccessTime.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (oldValue, newValue) -> oldValue, LinkedHashMap::new));
        for (Map.Entry<String, Date> set : sortedResult.entrySet()) {
            String key = set.getKey();
            String image = convertS3ObjectKeyToImageString(key);
            S3Object o = getS3Object(store, image);
            String tag = o.getObjectContent().toString();
            ImageTagStoreContent imageTagStoreContent = new ImageTagStoreContent(image, tag);
            imageNameWithTagSortedByAccessDate.add(imageTagStoreContent);
        }
        return imageNameWithTagSortedByAccessDate;
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
