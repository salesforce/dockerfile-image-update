package com.salesforce.dockerfileimageupdate.utils;

import com.amazonaws.services.s3.AmazonS3;

public class S3BucketUtil {
    private final AmazonS3 s3;

    public S3BucketUtil(AmazonS3 s3) {
        this.s3 = s3;
    }

    public AmazonS3 getS3Client() { return this.s3;}
}
