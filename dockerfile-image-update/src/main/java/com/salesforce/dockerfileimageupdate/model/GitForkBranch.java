package com.salesforce.dockerfileimageupdate.model;

// Converts an imageName to a branchName. Primary conversion necessary is : to -
// Also support backward compatible method of specifying a branch
//
// Container name rules: https://docs.docker.com/engine/reference/commandline/tag/#extended-description
// Branch Rules: https://mirrors.edge.kernel.org/pub/software/scm/git/docs/git-check-ref-format.html
//
public class GitForkBranch {
    private final String branchPrefix;
    private final String imageName;
    private final String imageTag;
    private final boolean specifiedBranchOverride;

    public GitForkBranch(String imageName, String imageTag) {
        this(imageName, imageTag, null);
    }

    public GitForkBranch(String imageName, String imageTag, String specifiedBranch) {
        this.imageTag = (imageTag == null || imageTag.trim().isEmpty()) ? "" : imageTag.trim();
        this.imageName = (imageName == null || imageName.trim().isEmpty()) ? "" : imageName.trim();
        if (specifiedBranch == null || specifiedBranch.trim().isEmpty()) {
            if (this.imageName.isEmpty()) {
                throw new IllegalArgumentException("You must specify an imageName if not specifying a branch");
            }
            this.branchPrefix = this.imageName.replace(":", "-").toLowerCase();
            this.specifiedBranchOverride = false;
        } else {
            this.branchPrefix = specifiedBranch;
            this.specifiedBranchOverride = true;
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
        if (this.imageTag == null) {
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
     * Either the specified branch of a combo of normalized imageName-imageTag
     * @return
     */
    public String getBranchName() {
        if (this.imageTag == null || this.imageTag.trim().isEmpty()) {
            return this.branchPrefix;
        } else {
            return this.branchPrefix + "-" + this.imageTag;
        }
    }

    public String getImageName() {
        return imageName;
    }

    public String getImageTag() {
        return imageTag;
    }
}