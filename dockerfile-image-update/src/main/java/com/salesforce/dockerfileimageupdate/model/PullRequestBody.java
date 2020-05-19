package com.salesforce.dockerfileimageupdate.model;

public class PullRequestBody {
    public static final String BODY_TEMPLATE = "Changing `%s` to the latest tag: `%s`\n\nNew base image: `%s`";

    public static String getBody(String image, String tag) {
        return String.format(BODY_TEMPLATE, image, tag, image + ":" + tag);
    }
}
