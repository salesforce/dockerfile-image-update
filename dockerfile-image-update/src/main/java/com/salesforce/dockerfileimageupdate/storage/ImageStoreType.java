package com.salesforce.dockerfileimageupdate.storage;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ImageStoreType is an enum that contains that different types of image tag stores that are supported
 * @author amukhopadhyay
 */


public enum ImageStoreType {
    S3{
        @Override
        public ImageTagStore getStore(DockerfileGitHubUtil dockerfileGitHubUtil, String store) throws Exception {
            AmazonS3 s3;
            String awsRegion = System.getenv("AWS_DEFAULT_REGION");
            String awsAccessKeyId = System.getenv("AWS_ACCESS_KEY_ID");
            String awsSecretKeyId = System.getenv("AWS_SECRET_ACCESS_KEY");
            if (awsRegion == null || awsAccessKeyId == null || awsSecretKeyId == null) {
                log.error("Cannot initialize the S3 client. Required environment variables AWS_DEFAULT_REGION, AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY not available");
                throw new Exception(String.format("The S3 bucket with name %s does not exist. Cannot proceed.", store));
            } else {
                BasicAWSCredentials awsCreds = new BasicAWSCredentials(awsAccessKeyId, awsSecretKeyId);
                s3 = AmazonS3ClientBuilder.standard().withRegion(awsRegion).withCredentials(new AWSStaticCredentialsProvider(awsCreds)).build();
            }
            return new S3BackedImageTagStore(s3, store);
        }
    },
    GIT{
        @Override
        public ImageTagStore getStore(DockerfileGitHubUtil dockerfileGitHubUtil, String store) {
            return dockerfileGitHubUtil.getGitHubJsonStore(store);
        }
    };
    private static final Logger log = LoggerFactory.getLogger(ImageStoreType.class);

    public ImageTagStore getStore(DockerfileGitHubUtil dockerfileGitHubUtil, String store) throws Exception {
        return this.getStore(dockerfileGitHubUtil, store);
    }

}


