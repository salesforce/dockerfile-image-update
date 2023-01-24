package com.salesforce.dockerfileimageupdate.model;

import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

public class ImageKeyValuePair {
    private static final Logger log = LoggerFactory.getLogger(ImageKeyValuePair.class);
    private static final String IMAGE = "image";
    private static final String INVALID_IMAGE_VALUE = "It is not a valid value for image key.";

    /**
     * The name of the base image
     */
    private final String baseImageName;
    /**
     * The tag of the base image
     */
    private final String tag;
    /**
     * Comment starting with #
     */
    private final String comments;
    /**
     * Yaml Spacing #
     */
    private final String spaces;

    /**
     * Accepts an image key value pair line from a docker-compose file
     * See {@code isImageKeyValuePair} to ensure you're passing a valid line in.
     *
     * @param imageKeyValuePair an Image Key value pair from a docker-compose file e.g: image: imageName:imageTag
     */
    public ImageKeyValuePair(String imageKeyValuePair) {
        if (!isImageKeyValuePair(imageKeyValuePair)) {
            throw new IllegalArgumentException(INVALID_IMAGE_VALUE);
        }
        String lineWithoutComment = imageKeyValuePair;
        int commentIndex = imageKeyValuePair.indexOf("#");
        if (commentIndex >= 0) {
            comments = imageKeyValuePair.substring(commentIndex);
            lineWithoutComment = imageKeyValuePair.substring(0, commentIndex);
        } else {
            comments = null;
        }
        // Get Yaml spacing in variable
        if (lineWithoutComment.startsWith(" ")) {
            spaces = lineWithoutComment.substring(0, lineWithoutComment.indexOf(IMAGE));
        } else {
            spaces = "";
        }

        // Remove "image:" from remaining string
        String lineWithoutImageKey = lineWithoutComment.trim().
                replaceFirst(IMAGE, "").replaceFirst(":", "").
                trim();

        String[] imageAndTag = lineWithoutImageKey.split(":");
        if (StringUtils.isNotEmpty(lineWithoutImageKey) && imageAndTag.length > 0) {
            baseImageName = imageAndTag[0];
            if (imageAndTag.length > 1) {
                tag = imageAndTag[1];
            } else {
                tag = null;
            }
        } else {
            baseImageName = null;
            tag = null;
        }
    }

    /**
     * Internal API to get a new ComposeImageValuePair from an existing object
     * @param baseImageName baseImageName to add
     * @param tag tag to add
     * @param comments comments to add
     */
    private ImageKeyValuePair(String baseImageName, String tag, String comments, String spaces) {
        this.baseImageName = baseImageName;
        this.tag = tag;
        this.comments = comments;
        this.spaces = spaces;
    }

    /**
     *  Check if this {@code lineInFile} is a image instruction,
     *  it is referencing {@code imageName} as a base image,
     *  and the tag is not the same as {@code imageTag} (or there is no tag)
     * @param lineInFile Line a code file
     * @param imageName images name
     * @param imageTag tag for imageName
     * @return {@link Boolean} value isImageKeyValuePairWithThisImageAndOlderTag
     */
    public static boolean isImageKeyValuePairWithThisImageAndOlderTag(String lineInFile, String imageName, String imageTag) {
        if (ImageKeyValuePair.isImageKeyValuePair(lineInFile)) {
            ImageKeyValuePair imageKeyValuePair = new ImageKeyValuePair(lineInFile);
            return imageKeyValuePair.hasBaseImage(imageName)
                    && imageKeyValuePair.hasADifferentTag(imageTag)
                    && DockerfileGitHubUtil.isValidImageTag(imageKeyValuePair.getTag());
        }
        return false;
    }

    /**
     * Get a new {@code ComposeImageValuePair} the same as this but with the {@code tag} set as {@code newTag}
     * @param newTag the new image tag
     * @return a new image instruction with the new image tag
     */
    public ImageKeyValuePair getImageKeyValuePairWithNewTag(String newTag) {
        return new ImageKeyValuePair(baseImageName, newTag, comments, spaces);
    }

    /**
     * Determines whether the line is a image instruction line in a docker-compose.yaml
     * @param composeImageKeyValueLine a single line(key:value) from a docker-compose.yaml
     * @return the line is a image instruction line or not
     */
    public static boolean isImageKeyValuePair(String composeImageKeyValueLine) {
        if (StringUtils.isNotBlank(composeImageKeyValueLine)) {
            return composeImageKeyValueLine.trim().startsWith(ImageKeyValuePair.IMAGE);
        }
        return false;
    }

    /**
     * @return a String representation of a image instruction line in docker-compose.yaml file. No new line at the end
     */
    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder(spaces + IMAGE);
        stringBuilder.append(": ");
        stringBuilder.append(baseImageName);
        if (hasTag()) {
            stringBuilder.append(String.format(":%s", tag.trim()));
        }

        if (hasComments()) {
            stringBuilder.append(String.format(" %s", comments));
        }

        return stringBuilder.toString();
    }

    public String getBaseImageName() {
        return baseImageName;
    }

    /**
     * Check to see if the {@code baseImageName} in this object is the {@code imageToFind} without
     * the other details (e.g. registry)
     * @param imageToFind the image name to search for
     * @return is {@code baseImageName} the same as {@code imageToFind} without extra things like registry
     */
    public boolean hasBaseImage(String imageToFind) {
        return baseImageName != null &&
                imageToFind != null &&
                baseImageName.endsWith(imageToFind);
    }

    /**
     * @return whether the {@code ComposeImageValuePair} has a {@code tag}
     */
    public boolean hasTag() {
        return tag != null;
    }

    /**
     * Determines whether the {@code tag} and {@code expectedTag} are the same
     * @param expectedTag the tag to compare against ComposeImageValuePair's {@code tag}
     * @return {@code true} if the 2 tags are different
     */
    public boolean hasADifferentTag(String expectedTag) {
        if (tag == null && expectedTag == null) {
            return false;
        }
        if (tag == null || expectedTag == null) {
            return true;
        }
        return !tag.trim().equals(expectedTag.trim());
    }

    public String getTag() {
        return tag;
    }

    public boolean hasComments() {
        return comments != null;
    }

    public String getComments() {
        return comments;
    }
}
