package com.salesforce.dockerfileimageupdate.model;

import org.kohsuke.github.GHRepository;

/**
 * Models the fork, parent repo, and contentPath of content in GitHub to process for an update operation
 */
public class GitHubContentToProcess {
    private final GHRepository fork;
    private final GHRepository parent;
    private final String contentPath;

    public GitHubContentToProcess(GHRepository fork, GHRepository parent, String contentPath) {
        this.parent = parent;
        this.fork = fork;
        this.contentPath = contentPath;
    }

    public GHRepository getFork() {
        return fork;
    }

    public GHRepository getParent() {
        return parent;
    }

    public String getContentPath() {
        return contentPath;
    }

    @Override
    public String toString() {
        return String.format("content path: %s; fork: %s", contentPath, fork);
    }
}
