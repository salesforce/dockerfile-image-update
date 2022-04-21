package com.salesforce.dockerfileimageupdate.storage;

import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ImageTagStoreContent)) return false;
        ImageTagStoreContent that = (ImageTagStoreContent) o;
        return Objects.equals(getImageName(), that.getImageName()) && Objects.equals(getTag(), that.getTag());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getImageName(), getTag());
    }

    public String getTag() {
        return this.tag;
    }

}
