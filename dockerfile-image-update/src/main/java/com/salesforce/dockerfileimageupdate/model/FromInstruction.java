package com.salesforce.dockerfileimageupdate.model;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * This class represents a FROM instruction line in a Dockerfile and contains methods to determine whether a line
 * is a FROM instruction. For more information: https://docs.docker.com/engine/reference/builder/#from
 */
public class FromInstruction {

    private static final String NAME = "FROM";
    private static final String INVALID_INSTRUCTION_LINE = "You have not provided a valid FROM instruction line.";
    private static final String NO_DFIU = "no-dfiu";
    /**
     * The name of the base image
     */
    private final String baseImageName;
    /**
     * The tag of the base image
     */
    private final String tag;
    /**
     * As of writing, this could include {@code AS name} to run a multi-stage build
     */
    private final List<String> additionalParts;
    /**
     * Comment starting with #
     */
    private final String comments;

    /**
     * Accepts a FROM instruction line from a Dockerfile
     * See {@code isFromInstruction} to ensure you're passing a valid line in.
     *
     * @param fromInstructionLine a FROM instruction line from a Dockerfile
     */
    public FromInstruction(String fromInstructionLine) {
        if (!isFromInstruction(fromInstructionLine)) {
            throw new IllegalArgumentException(INVALID_INSTRUCTION_LINE);
        }
        String lineWithoutComment = fromInstructionLine;
        int commentIndex = fromInstructionLine.indexOf("#");
        if (commentIndex >= 0) {
            comments = fromInstructionLine.substring(commentIndex);
            lineWithoutComment = fromInstructionLine.substring(0, commentIndex);
        } else {
            comments = null;
        }
        // Trim the space now that we don't have comments to worry about
        lineWithoutComment = lineWithoutComment.trim();
        String[] lineParts = lineWithoutComment.split("\\s+");

        if (lineParts.length > 1) {
            // The image will be after the FROM part
            String dockerFileImage = lineParts[1];
            String[] imageAndTag = dockerFileImage.split(":");
            baseImageName = imageAndTag[0];

            if (imageAndTag.length > 1) {
                tag = imageAndTag[1];
            } else {
                tag = null;
            }

            if (lineParts.length > 2) {
                additionalParts = ImmutableList.copyOf(Arrays.asList(lineParts).subList(2, lineParts.length));
            } else {
                additionalParts = ImmutableList.of();
            }
        } else {
            baseImageName = null;
            tag = null;
            additionalParts = ImmutableList.of();
        }
    }

    /**
     * Internal API to get a new FromInstruction from an existing object
     * @param baseImageName baseImageName to add
     * @param tag tag to add
     * @param additionalParts additionalParts to add
     * @param comments comments to add
     */
    private FromInstruction(String baseImageName, String tag, List<String> additionalParts, String comments) {
        this.baseImageName = baseImageName;
        this.tag = tag;
        this.additionalParts = ImmutableList.copyOf(additionalParts);
        this.comments = comments;
    }

    /**
     * Check if this {@code lineInFile} is a FROM instruction, it is referencing {@code imageName} as a base image,
     * and the tag is not the same as {@code imageTag} (or there is no tag)
     *
     * @param lineInFile a single line from a file
     * @param imageName the base image name we're looking for
     * @param imageTag the proposed new tag
     */
    public static boolean isFromInstructionWithThisImageAndOlderTag(String lineInFile, String imageName, String imageTag) {
        if (FromInstruction.isFromInstruction(lineInFile)) {
            FromInstruction fromInstruction = new FromInstruction(lineInFile);
            return fromInstruction.hasBaseImage(imageName) && fromInstruction.hasADifferentTag(imageTag);
        }
        return false;
    }

    /**
     * Get a new {@code FromInstruction} the same as this but with the {@code tag} set as {@code newTag}
     * @param newTag the new image tag
     * @return a new FROM with the new image tag
     */
    public FromInstruction getFromInstructionWithNewTag(String newTag) {
        return new FromInstruction(baseImageName, newTag, additionalParts, comments);
    }

    /**
     * Determines whether the line is a FROM instruction line in a Dockerfile
     * @param dockerFileLine a single line from a Dockerfile
     * @return the line is a FROM instruction line or not
     */
    public static boolean isFromInstruction(String dockerFileLine) {
        if (StringUtils.isNotBlank(dockerFileLine)) {
            return dockerFileLine.trim().startsWith(FromInstruction.NAME);
        }
        return false;
    }

    /**
     * @return a String representation of a FROM instruction line in Dockerfile. No new line at the end
     */
    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder(NAME);
        stringBuilder.append(" ");
        stringBuilder.append(baseImageName);
        if (hasTag()) {
            stringBuilder.append(String.format(":%s", tag.trim()));
        }
        for (String part : additionalParts) {
            if (StringUtils.isNotBlank(part)) {
                stringBuilder.append(String.format(" %s", part.trim()));
            }
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
     * @return whether the {@code FromInstruction} has a {@code tag}
     */
    public boolean hasTag() {
        return tag != null;
    }

    /**
     * Determines whether the {@code tag} and {@code expectedTag} are the same
     * @param expectedTag the tag to compare against FromInstruction's {@code tag}
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

    /**
     * Determines whether the comment has mentioned "no-dfiu" to ignore creating dfiu PR
     */
    public boolean hasCommentNoDfiu() {
        if (hasComments()) {
            return comments.contains(NO_DFIU);
        }
        return false;
    }

    public String getTag() {
        return tag;
    }

    public List<String> getAdditionalParts() {
        return additionalParts;
    }

    public boolean hasComments() {
        return comments != null;
    }

    public String getComments() {
        return comments;
    }
}
