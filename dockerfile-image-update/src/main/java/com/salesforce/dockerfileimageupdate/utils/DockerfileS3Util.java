package com.salesforce.dockerfileimageupdate.utils;

import com.amazonaws.services.s3.model.Bucket;
import com.salesforce.dockerfileimageupdate.storage.GitHubJsonStore;
import com.salesforce.dockerfileimageupdate.storage.S3Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DockerfileS3Util {
    private static final Logger log = LoggerFactory.getLogger(DockerfileGitHubUtil.class);
    private final S3BucketUtil s3BucketUtil;

    public DockerfileS3Util(S3BucketUtil s3BucketUtil) {
        this.s3BucketUtil = s3BucketUtil;
    }

    public S3Store getS3ImageStore(String store) {
        return new S3Store(this.s3BucketUtil, store);
    }

    public S3BucketUtil getS3BucketUtil() {
        return s3BucketUtil;
    }
}
