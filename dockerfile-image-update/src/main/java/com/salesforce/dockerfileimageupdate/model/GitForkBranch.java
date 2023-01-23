package com.salesforce.dockerfileimageupdate.model;

import com.salesforce.dockerfileimageupdate.utils.Constants;
import org.apache.commons.lang3.StringUtils;

/**
 * Converts an imageName to a branchName. Primary conversion necessary is : to -
 * Also support backward compatible method of specifying a branch
 *
 * Container name rules: https://docs.docker.com/engine/reference/commandline/tag/#extended-description
 * Branch Rules: https://mirrors.edge.kernel.org/pub/software/scm/git/docs/git-check-ref-format.html
 */
public class GitForkBranch {
    private final String branchPrefix;
    private final String branchSuffix;
    private final String imageName;
    private final String imageTag;
    private final boolean specifiedBranchOverride;

    public GitForkBranch(String imageName, String imageTag, String specifiedBranch, String filenamesSearchedFor) {
        this.imageTag = (imageTag == null || imageTag.trim().isEmpty()) ? "" : imageTag.trim();
        this.imageName = (imageName == null || imageName.trim().isEmpty()) ? "" : imageName.trim();
        if (specifiedBranch == null || specifiedBranch.trim().isEmpty()) {
            if (this.imageName.isEmpty()) {
                throw new IllegalArgumentException("You must specify an imageName if not specifying a branch");
            }
            this.branchPrefix = this.imageName.replace(":", "-").toLowerCase();
            this.specifiedBranchOverride = false;
            this.branchSuffix = getBranchSuffix(StringUtils.isNotBlank(filenamesSearchedFor) ? filenamesSearchedFor.toLowerCase():filenamesSearchedFor);
        } else {
            this.branchPrefix = specifiedBranch;
            this.specifiedBranchOverride = true;
            this.branchSuffix = "";
        }
    }

    /**
     * If a specifiedBranch was specified, do a direct comparison. Otherwise, check for a
     * translated imageName as a prefix.
     *
     * @param branchName branch name to check against our new image
     * @return is the branch name equal to specifiedBranch or the normalized image name
     */
    public boolean isSameBranchOrHasImageNamePrefix(String branchName) {
        if (this.imageTag.equals("")) {
            return this.getBranchName().equals(branchName);
        } else if (branchName != null) {
            String tempBranchName = branchName.trim();
            return getBranchWithoutTag(tempBranchName).equals(this.branchPrefix);
        }
        return false;
    }

    private String getBranchWithoutTag(String branchName) {
        int lastDash = branchName.lastIndexOf("-");
        if (lastDash == -1) {
            return branchName;
        } else {
            return branchName.substring(0, lastDash);
        }
    }

    /**
     * @return a branch was specified to override default behavior
     */
    public boolean useSpecifiedBranchOverride() {
        return this.specifiedBranchOverride;
    }

    /**
     * Either the specified branch or a combo of normalized imageName-imageTag
     *
     * This essentially uses a cleansed and lowercase imageName as the branch prefix.
     * If an imageTag exists, we append a dash and then the imageTag.
     *
     * @return cleansed branchPrefix[-imageTag]
     */
    public String getBranchName() {
        if (this.imageTag == null || this.imageTag.trim().isEmpty()) {
            return this.branchPrefix + this.branchSuffix;
        } else {
            return this.branchPrefix + "-" + this.imageTag + this.branchSuffix;
        }
    }

    public String getImageName() {
        return imageName;
    }

    public String getImageTag() {
        return imageTag;
    }

    private String getBranchSuffix(String filenamesSearchedFor) {
        // To avoid branch with same imageName-tag getting overwritten in case multiple runs(with different file types)
        // on same image tag store, adding suffix to branch name
        if (StringUtils.isBlank(filenamesSearchedFor)) {
            return "";
        } else if (filenamesSearchedFor.contains(Constants.FILENAME_DOCKERFILE) && !filenamesSearchedFor.contains(Constants.FILENAME_DOCKER_COMPOSE)) {
            return "_dockerfile";
        } else if (filenamesSearchedFor.contains(Constants.FILENAME_DOCKER_COMPOSE) && !filenamesSearchedFor.contains(Constants.FILENAME_DOCKERFILE)) {
            return "_dockercompose";
        } else {
            return "";
        }
    }
}