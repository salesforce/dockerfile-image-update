package com.salesforce.dockerfileimageupdate.storage;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;

/**
 * ImageStoreType is an enum that contains that different types of image tag stores that are supported
 * @author amukhopadhyay
 */


public enum ImageStoreType {
    S3{
        @Override
        public ImageTagStore getStore(DockerfileGitHubUtil dockerfileGitHubUtil, String store) {
            String awsRegion = System.getenv("AWS_DEFAULT_REGION");
            String awsAccessKeyId = System.getenv("AWS_ACCESS_KEY_ID");
            String awsSecretKeyId = System.getenv("AWS_SECRET_ACCESS_KEY");
            BasicAWSCredentials awsCreds = new BasicAWSCredentials(awsAccessKeyId, awsSecretKeyId);
            AmazonS3 s3 = AmazonS3ClientBuilder.standard().withRegion(awsRegion).withCredentials(new AWSStaticCredentialsProvider(awsCreds)).build();
            return new S3BackedImageTagStore(s3, store);
        }
    },
    GIT{
        @Override
        public ImageTagStore getStore(DockerfileGitHubUtil dockerfileGitHubUtil, String store) {
            return dockerfileGitHubUtil.getGitHubJsonStore(store);
        }
    };

    public ImageTagStore getStore(DockerfileGitHubUtil dockerfileGitHubUtil, String store) {
        return this.getStore(dockerfileGitHubUtil, store);
    }

}


