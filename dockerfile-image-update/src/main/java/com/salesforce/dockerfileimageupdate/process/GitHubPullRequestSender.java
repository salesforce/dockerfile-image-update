package com.salesforce.dockerfileimageupdate.process;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.salesforce.dockerfileimageupdate.model.GitHubContentToProcess;
import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.PagedSearchIterable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Optional;

public class GitHubPullRequestSender {
    private static final Logger log = LoggerFactory.getLogger(GitHubPullRequestSender.class);
    private final DockerfileGitHubUtil dockerfileGitHubUtil;

    public GitHubPullRequestSender(DockerfileGitHubUtil dockerfileGitHubUtil) {
        this.dockerfileGitHubUtil = dockerfileGitHubUtil;
    }

    /* There is a separation here with forking and performing the Dockerfile update. This is because of the delay
     * on Github, where after the fork, there may be a time gap between repository creation and content replication
     * when forking. So, in hopes of alleviating the situation a little bit, we do all the forking before the
     * Dockerfile updates.
     *
     * NOTE: We are not currently forking repositories that are already forks
     */
    public Multimap<String, GitHubContentToProcess> forkRepositoriesFoundAndGetPathToDockerfiles(PagedSearchIterable<GHContent> contentsWithImage) throws IOException {
        log.info("Forking repositories...");
        Multimap<String, GitHubContentToProcess> pathToDockerfilesInParentRepo = HashMultimap.create();
        GHRepository parent;
        String parentRepoName;
        for (GHContent c : contentsWithImage) {
            /* Kohsuke's GitHub API library, when retrieving the forked repository, looks at the name of the parent to
             * retrieve. The issue with that is: GitHub, when forking two or more repositories with the same name,
             * automatically fixes the names to be unique (by appending "-#" to the end). Because of this edge case, we
             * cannot save the forks and iterate over the repositories; else, we end up missing/not updating the
             * repositories that were automatically fixed by GitHub. Instead, we save the names of the parent repos
             * in the map above, find the list of repositories under the authorized user, and iterate through that list.
             */
            parent = c.getOwner();
            parentRepoName = parent.getFullName();
            // TODO: Error check... Refresh the repo to ensure that the object has full details
            parent = dockerfileGitHubUtil.getRepo(parentRepoName);
            if (parent.isFork()) {
                log.warn("Skipping {} because it's a fork already. Sending a PR to a fork is unsupported at the moment.",
                        parentRepoName);
            } else if (parent.isArchived()) {
                log.warn("Skipping {} because it's archived.", parent.getFullName());
            } else if (dockerfileGitHubUtil.thisUserIsOwner(parent)) {
                log.warn("Skipping {} because it is owned by this user.", parent.getFullName());
            } else {
                // fork the parent if not already forked
                GHRepository fork;
                if (pathToDockerfilesInParentRepo.containsKey(parentRepoName)) {
                    // Found more content for this fork, so add it as well
                    Collection<GitHubContentToProcess> gitHubContentToProcesses = pathToDockerfilesInParentRepo.get(parentRepoName);
                    Optional<GitHubContentToProcess> firstForkData = gitHubContentToProcesses.stream().findFirst();
                    if (firstForkData.isPresent()) {
                        fork = firstForkData.get().getFork();
                        pathToDockerfilesInParentRepo.put(parentRepoName, new GitHubContentToProcess(fork, parent, c.getPath()));
                    } else {
                        log.warn("For some reason we have ");
                    }
                } else {
                    log.info("Getting or creating fork: {}", parentRepoName);
                    fork = dockerfileGitHubUtil.getOrCreateFork(parent);
//                    fork = null;
                    if (fork == null) {
                        log.info("Could not fork {}", parentRepoName);
                    } else {
                        // Add repos to pathToDockerfilesInParentRepo only if we forked it successfully.
                        pathToDockerfilesInParentRepo.put(parentRepoName, new GitHubContentToProcess(fork, parent, c.getPath()));
                    }
                }
            }
        }

        log.info("Path to Dockerfiles in repos: {}", pathToDockerfilesInParentRepo);

        return pathToDockerfilesInParentRepo;
    }
}
