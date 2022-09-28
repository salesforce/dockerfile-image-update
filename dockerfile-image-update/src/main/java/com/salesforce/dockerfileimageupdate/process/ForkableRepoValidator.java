package com.salesforce.dockerfileimageupdate.process;

import com.salesforce.dockerfileimageupdate.model.FromInstruction;
import com.salesforce.dockerfileimageupdate.model.GitForkBranch;
import com.salesforce.dockerfileimageupdate.model.ShouldForkResult;
import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import static com.salesforce.dockerfileimageupdate.model.ShouldForkResult.shouldForkResult;
import static com.salesforce.dockerfileimageupdate.model.ShouldForkResult.shouldNotForkResult;

public class ForkableRepoValidator {
    private static final Logger log = LoggerFactory.getLogger(ForkableRepoValidator.class);

    public static final String REPO_IS_FORK =
            "it's a fork already. Sending a PR to a fork is unsupported at the moment.";
    public static final String REPO_IS_ARCHIVED = "it's archived.";
    public static final String REPO_IS_OWNED_BY_THIS_USER = "it is owned by this user.";
    public static final String COULD_NOT_CHECK_THIS_USER =
            "we could not determine fork status because we don't know the identity of the authenticated user.";
    public static final String CONTENT_PATH_NOT_IN_DEFAULT_BRANCH_TEMPLATE =
            "didn't find content path %s in default branch";
    public static final String COULD_NOT_FIND_IMAGE_TO_UPDATE_TEMPLATE =
            "didn't find the image '%s' which required an update in path %s";
    private final DockerfileGitHubUtil dockerfileGitHubUtil;

    public ForkableRepoValidator(DockerfileGitHubUtil dockerfileGitHubUtil) {
        this.dockerfileGitHubUtil = dockerfileGitHubUtil;
    }

    /**
     * Check various conditions required for a repo to qualify for updates
     *
     * @param parentRepo parent repo we're checking
     * @param searchResultContent search result content we'll check against
     * @param gitForkBranch forked git branch
     * @return should we fork the parentRepo?
     */
    public ShouldForkResult shouldFork(GHRepository parentRepo,
                                       GHContent searchResultContent,
                                       GitForkBranch gitForkBranch) {
        return parentIsFork(parentRepo)
                .and(parentIsArchived(parentRepo)
                        .and(thisUserIsNotOwner(parentRepo)
                                .and(contentHasChangesInDefaultBranch(parentRepo, searchResultContent, gitForkBranch))));
    }

    /**
     * Check to see if the default branch of the parentRepo has the path we found in searchResultContent and
     * whether that content has a qualifying base image update
     * @param parentRepo parentRepo which we'd fork off of
     * @param searchResultContent search result with path to check in parent repo's default branch (where we'd PR)
     * @param gitForkBranch information about the imageName we'd like to update with the new tag.
     * @return {@code ShouldForkResult } Object
     */
    protected ShouldForkResult contentHasChangesInDefaultBranch(GHRepository parentRepo,
                                                                GHContent searchResultContent,
                                                                GitForkBranch gitForkBranch) {
        try {
            String searchContentPath = searchResultContent.getPath();
            GHContent content =
                    dockerfileGitHubUtil.tryRetrievingContent(parentRepo,
                            searchContentPath, parentRepo.getDefaultBranch());
            if (content == null) {
                return shouldNotForkResult(
                        String.format(CONTENT_PATH_NOT_IN_DEFAULT_BRANCH_TEMPLATE, searchContentPath));
            } else {
                if (hasNoChanges(content, gitForkBranch)) {
                    return shouldNotForkResult(
                            String.format(COULD_NOT_FIND_IMAGE_TO_UPDATE_TEMPLATE,
                                    gitForkBranch.getImageName(), searchContentPath));
                }
            }
        } catch (InterruptedException e) {
            log.warn("Couldn't get parent content to check for some reason for {}. Trying to proceed... exception: {}",
                    parentRepo.getFullName(), e.getMessage());
        }
        return shouldForkResult();
    }

    /**
     * Check to see whether there are any changes in the specified content where the specified base image
     * in gitForkBranch needs an update
     *
     * @param content content to check
     * @param gitForkBranch information about the base image we'd like to update
     * @return {@code Boolean} value signifying whether there are any changes content
     */
    protected boolean hasNoChanges(GHContent content, GitForkBranch gitForkBranch) {
        try (InputStream stream = content.read();
             InputStreamReader streamR = new InputStreamReader(stream);
             BufferedReader reader = new BufferedReader(streamR)) {
            String line;
            while ( (line = reader.readLine()) != null ) {
                if (FromInstruction.isFromInstructionWithThisImageAndOlderTag(line,
                        gitForkBranch.getImageName(), gitForkBranch.getImageTag())) {
                    return false;
                }
            }
        } catch (IOException exception) {
            log.warn("Failed while checking if there are changes in {}. Skipping... exception: {}",
                    content.getPath(), exception.getMessage());
        }
        return true;
    }

    /**
     * Attempts to check to see if this user is the owner of the repo (don't fork your own repo).
     * If we can't tell because of a systemic error, don't attempt to fork (we could perhaps loosen this in the future).
     * If this user is the owner of the repo, do not fork.
     *
     * @param parentRepo parent repo we're checking
     * @return {@code ShouldForkResult} Object abstracting data if the user is owner of the repo
     */
    protected ShouldForkResult thisUserIsNotOwner(GHRepository parentRepo) {
        try {
            if (dockerfileGitHubUtil.thisUserIsOwner(parentRepo)) {
                return shouldNotForkResult(REPO_IS_OWNED_BY_THIS_USER);
            }
        } catch (IOException ioException) {
            return shouldNotForkResult(COULD_NOT_CHECK_THIS_USER);
        }
        return shouldForkResult();
    }

    /**
     * Check if parentRepo is a fork. Do not fork a fork (for now, at least).
     *
     * @param parentRepo parent repo we're checking
     * @return {@code ShouldForkResult} Object abstracting data if parentRepo is a fork
     */
    protected ShouldForkResult parentIsFork(GHRepository parentRepo) {
        return parentRepo.isFork() ? shouldNotForkResult(REPO_IS_FORK) : shouldForkResult();
    }

    /**
     * Check if parentRepo is archived. We won't be able to update it, so do not fork.
     *
     * @param parentRepo parent repo we're checking
     * @return {@code ShouldForkResult} Object abstracting data if parentRepo is archived
     */
    protected ShouldForkResult parentIsArchived(GHRepository parentRepo) {
        return parentRepo.isArchived() ? shouldNotForkResult(REPO_IS_ARCHIVED) : shouldForkResult();

    }
}
