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
import java.util.Optional;

public class GitHubPullRequestSender {
    private static final Logger log = LoggerFactory.getLogger(GitHubPullRequestSender.class);
    public static final String REPO_IS_FORK = "it's a fork already. Sending a PR to a fork is unsupported at the moment.";
    public static final String REPO_IS_ARCHIVED = "it's archived.";
    public static final String REPO_IS_OWNED_BY_THIS_USER = "it is owned by this user.";
    public static final String COULD_NOT_CHECK_THIS_USER = "we could not determine fork status because we don't know the identity of the authenticated user.";
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
            // TODO: Error check... Refresh the repo to ensure that the object has full details
            parent = dockerfileGitHubUtil.getRepo(parentRepoName);
            Optional<String> shouldNotForkRepo = shouldNotForkRepo(parent);
            if (shouldNotForkRepo.isPresent()) {
                log.warn("Skipping {} because {}", parentRepoName, shouldNotForkRepo.get());
            } else {
                // fork the parent if not already forked
                ensureForkedAndAddToListForProcessing(pathToDockerfilesInParentRepo, parent, parentRepoName, ghContent);
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

    /**
     * Returns a optional with the reason if we should not fork a repo else returns an empty optional if we should fork.
     * @param parentRepo The parent repo which may or may not be a candidate to fork
     */
    protected Optional<String> shouldNotForkRepo(GHRepository parentRepo) {
        Optional<String> result = Optional.empty();
        if (parentRepo.isFork()) {
            result = Optional.of(REPO_IS_FORK);
        } else if (parentRepo.isArchived()) {
            result = Optional.of(REPO_IS_ARCHIVED);
        } else {
            try {
                if (dockerfileGitHubUtil.thisUserIsOwner(parentRepo)) {
                    result = Optional.of(REPO_IS_OWNED_BY_THIS_USER);
                }
            } catch (IOException ioException) {
                result = Optional.of(COULD_NOT_CHECK_THIS_USER);
            }
        }
        return result;
    }
}
