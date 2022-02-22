package com.salesforce.dockerfileimageupdate.utils;

import java.util.Optional;

public class ProcessingErrors {
    private final String imageNameAndTag;
    private final Optional<Exception> failure;

    public ProcessingErrors(String imageNameAndTag, Optional<Exception> failure) {
        this.imageNameAndTag = imageNameAndTag;
        this.failure = failure;
    }

    public String getImageNameAndTag() {
        return imageNameAndTag;
    }

    public Optional<Exception> getFailure() {
        return failure;
    }
}
