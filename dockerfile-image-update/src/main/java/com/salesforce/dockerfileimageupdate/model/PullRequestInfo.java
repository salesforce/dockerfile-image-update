package com.salesforce.dockerfileimageupdate.model;

import com.salesforce.dockerfileimageupdate.utils.Constants;

public class PullRequestInfo {
    public static final String BODY_TEMPLATE =
            "`%s` changed recently. This pull request ensures you're using the latest version of the image and " +
            "changes `%s` to the latest tag: `%s`\n" +
            "\n" +
            "New base image: `%s`";
    public static final String DEFAULT_TITLE = "Automatic Dockerfile Image Updater";
    public static final String OLD_CONSTANT_BODY = Constants.PULL_REQ_ID;
    private final String title;
    private final String image;
    private final String tag;

    public PullRequestInfo(String title, String image, String tag) {
        if (title == null || title.trim().isEmpty()) {
            this.title = DEFAULT_TITLE;
        } else {
            this.title = title;
        }
        this.image = image;
        this.tag = tag;
    }

    /**
     * @return title for the pull request
     */
    public String getTitle() {
        return title;
    }

    /**
     * @return formatted pull request body
     */
    public String getBody() {
        if (this.image == null && this.tag == null) {
            return OLD_CONSTANT_BODY;
        }
        return String.format(BODY_TEMPLATE, image, image, tag, image + ":" + tag);
    }
}
