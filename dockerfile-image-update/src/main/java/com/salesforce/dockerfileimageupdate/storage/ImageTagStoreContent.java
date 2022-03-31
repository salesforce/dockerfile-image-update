package com.salesforce.dockerfileimageupdate.storage;

/**
 * ImageTagStoreContent is the main entity we'll be using to get the content of the Image tag store
 * @author amukhopadhyay
 */
public class ImageTagStoreContent {

    private final String imageName;
    private final String tag;

    public ImageTagStoreContent(String imageName, String tag) {
        this.imageName = imageName;
        this.tag = tag;
    }

    public String getImageName() {
        return this.imageName;
    }

    public String getTag() {
        return this.tag;
    }

}
