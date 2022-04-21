package com.salesforce.dockerfileimageupdate.utils;

import java.util.Optional;

public class ProcessingErrors {
    private final String imageName;
    private final String tag;
    private final Optional<Exception> failure;

    public ProcessingErrors(String imageName, String tag, Optional<Exception> failure) {
        this.imageName = imageName;
        this.tag = tag;
        this.failure = failure;
    }

    public String getImageName() {
        return imageName;
    }

    public String getTag() {
        return tag;
    }

    public Optional<Exception> getFailure() {
        return failure;
    }
}
