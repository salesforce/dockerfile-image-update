package com.salesforce.dockerfileimageupdate.process;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.salesforce.dockerfileimageupdate.model.GitForkBranch;
import com.salesforce.dockerfileimageupdate.model.GitHubContentToProcess;
import com.salesforce.dockerfileimageupdate.model.ShouldForkResult;
import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedSearchIterable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

public class GitHubPullRequestSender {
    private static final Logger log = LoggerFactory.getLogger(GitHubPullRequestSender.class);
    public static final String REPO_IS_FORK = "it's a fork already. Sending a PR to a fork is unsupported at the moment.";
    private final DockerfileGitHubUtil dockerfileGitHubUtil;
    private final ForkableRepoValidator forkableRepoValidator;

    public GitHubPullRequestSender(DockerfileGitHubUtil dockerfileGitHubUtil, ForkableRepoValidator forkableRepoValidator) {
        this.dockerfileGitHubUtil = dockerfileGitHubUtil;
        this.forkableRepoValidator = forkableRepoValidator;
    }

    /* There is a separation here with forking and performing the Dockerfile update. This is because of the delay
     * on Github, where after the fork, there may be a time gap between repository creation and content replication
     * when forking. So, in hopes of alleviating the situation a little bit, we do all the forking before the
     * Dockerfile updates.
     *
     * NOTE: We are not currently forking repositories that are already forks
     */
    public Multimap<String, GitHubContentToProcess> forkRepositoriesFoundAndGetPathToDockerfiles(
            PagedSearchIterable<GHContent> contentsWithImage,
            GitForkBranch gitForkBranch) {
        log.info("Forking repositories...");
        Multimap<String, GitHubContentToProcess> pathToDockerfilesInParentRepo = HashMultimap.create();
        GHRepository parent;
        String parentRepoName;
        for (GHContent ghContent : contentsWithImage) {
            /* Kohsuke's GitHub API library, when retrieving the forked repository, looks at the name of the parent to
             * retrieve. The issue with that is: GitHub, when forking two or more repositories with the same name,
             * automatically fixes the names to be unique (by appending "-#" to the end). Because of this edge case, we
             * cannot save the forks and iterate over the repositories; else, we end up missing/not updating the
             * repositories that were automatically fixed by GitHub. Instead, we save the names of the parent repos
             * in the map above, find the list of repositories under the authorized user, and iterate through that list.
             */
            parent = ghContent.getOwner();
            parentRepoName = parent.getFullName();
            // Refresh the repo to ensure that the object has full details
            try {
                parent = dockerfileGitHubUtil.getRepo(parentRepoName);
                ShouldForkResult shouldForkResult = forkableRepoValidator.shouldFork(parent, ghContent, gitForkBranch);
                if (shouldForkResult.isForkable()) {
                    // fork the parent if not already forked
                    ensureForkedAndAddToListForProcessing(pathToDockerfilesInParentRepo, parent, parentRepoName, ghContent);
                } else {
                    log.warn("Skipping {} because {}", parentRepoName, shouldForkResult.getReason());
                }
            } catch (IOException exception) {
                log.warn("Could not refresh details of {}", parentRepoName);
            }
        }

        log.info("Path to Dockerfiles in repos: {}", pathToDockerfilesInParentRepo);

        return pathToDockerfilesInParentRepo;
    }

    /**
     * Ensure that we have forked this repository if it hasn't already been forked and add the content for processing
     *
     * @param pathToDockerfilesInParentRepo multimap for current and later processing
     * @param parent the actual parent repo with content
     * @param parentRepoName the name of the parent repo
     * @param ghContent the content that was found which should have this image
     */
    private void ensureForkedAndAddToListForProcessing(Multimap<String, GitHubContentToProcess> pathToDockerfilesInParentRepo,
                                                       GHRepository parent,
                                                       String parentRepoName,
                                                       GHContent ghContent) {
        GHRepository fork = getForkFromExistingRecordToProcess(pathToDockerfilesInParentRepo, parentRepoName);
        if (fork == null) {
            log.info("Getting or creating fork: {}", parentRepoName);
            fork = dockerfileGitHubUtil.getOrCreateFork(parent);
        }
        // Add repos to pathToDockerfilesInParentRepo only if we forked it successfully.
        if (fork == null) {
            log.info("Could not fork {}", parentRepoName);
        } else {
            pathToDockerfilesInParentRepo.put(parentRepoName, new GitHubContentToProcess(fork, parent, ghContent.getPath()));
        }
    }

    /**
     * If there's an existing record, return the fork from that.
     *
     * @param pathToDockerfilesInParentRepo processing multimap
     * @param parentRepoName name of parent repo to find
     */
    protected GHRepository getForkFromExistingRecordToProcess(Multimap<String, GitHubContentToProcess> pathToDockerfilesInParentRepo,
                                                            String parentRepoName) {
        if (pathToDockerfilesInParentRepo.containsKey(parentRepoName)) {
            Optional<GitHubContentToProcess> firstForkData = pathToDockerfilesInParentRepo.get(parentRepoName).stream().findFirst();
            if (firstForkData.isPresent()) {
                return firstForkData.get().getFork();
            } else {
                log.warn("For some reason we have data inconsistency when trying to find the fork for {}", parentRepoName);
            }
        }
        return null;
    }
}
