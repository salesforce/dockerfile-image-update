package com.salesforce.dockerfileimageupdate.storage;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
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

    /**
     * This method updates the image tag store backed by S3 by updating the image version for the image name passed.
     *
     * @param img the name of the image that needs to be updated.
     * @param tag the version of the image that it needs to update to.
     */
    public void updateStore(String img, String tag) throws IOException {
        log.info("Updating store: {} with image: {} tag: {}...", store, img, tag);
        if (s3.doesBucketExistV2(store)) {
            String key = convertImageStringToS3ObjectKey(img);
            s3.putObject(store, key, tag);
        } else {
            throw new IOException(String.format("The S3 bucket with name %s does not exist. Cannot proceed.", store));
        }

    }

    /**
     * This method gets the content of the image tag store backed by S3.
     *
     * @param dockerfileGitHubUtil
     * @param storeName The name of the store.
     * @return List of ImageTagStoreContent objects that contain the image name and the image tag.
     */
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

    private List<ImageTagStoreContent> getStoreContentSortedByAccessDate(Map<String, Date> imageNameWithAccessTime) throws InterruptedException {
        List<ImageTagStoreContent> imageNameWithTagSortedByAccessDate = new ArrayList<>();
        LinkedHashMap<String, Date> sortedResult = new LinkedHashMap<>();
        // Sort the content by the access date so that the file which was accessed most recently gets processed first
        imageNameWithAccessTime.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .forEachOrdered(x -> sortedResult.put(x.getKey(), x.getValue()));

        for (Map.Entry<String, Date> set : sortedResult.entrySet()) {
            String key = set.getKey();
            String image = convertS3ObjectKeyToImageString(key);
            S3Object o = getS3Object(store, image);
            String tag = getTagValueFromObject(o);
            ImageTagStoreContent imageTagStoreContent = new ImageTagStoreContent(image, tag);
            imageNameWithTagSortedByAccessDate.add(imageTagStoreContent);
        }
        return imageNameWithTagSortedByAccessDate;
    }

    private String getTagValueFromObject(S3Object o) {
        String tag = "";
        try (S3ObjectInputStream is = o.getObjectContent()) {
            tag = CharStreams.toString(new InputStreamReader(is, Charsets.UTF_8));
        } catch (IOException e) {
            log.error("Could not get the tag value. Exception: ", e.getMessage());
        }
        return tag;
    }

    private String convertImageStringToS3ObjectKey(String img) {
        return img.replace('/', S3_FILE_KEY_PATH_DELIMITER);
    }

    private String convertS3ObjectKeyToImageString(String key) {
        return key.replace(S3_FILE_KEY_PATH_DELIMITER, '/');
    }

    private ListObjectsV2Result getS3Objects() {
        return s3.listObjectsV2(store);
    }

    private S3Object getS3Object(String store, String key) {
        return s3.getObject(store, key);
    }
}
