package com.salesforce.dockerfileimageupdate.process;

import com.salesforce.dockerfileimageupdate.model.GitForkBranch;
import com.salesforce.dockerfileimageupdate.model.ShouldForkResult;
import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

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
     * @return should we fork the parentRepo?
     */
    public ShouldForkResult shouldFork(GHRepository parentRepo,
                                       GHContent searchResultContent,
                                       GitForkBranch gitForkBranch) {
        return parentIsFork(parentRepo)
                .and(parentIsArchived(parentRepo)
                        .and(thisUserIsNotOwner(parentRepo)));
//                                .and(contentExistsInDefaultBranch(parentRepo, searchResultContent, gitForkBranch))));
    }

//    protected ShouldForkResult contentExistsInDefaultBranch(GHRepository parentRepo,
//                                                            GHContent searchResultContent,
//                                                            GitForkBranch gitForkBranch) {
//        try {
//            String searchContentPath = searchResultContent.getPath();
//            GHContent content =
//                    dockerfileGitHubUtil.tryRetrievingContent(parentRepo,
//                            searchContentPath, parentRepo.getDefaultBranch());
//            if (content == null) {
//                return shouldNotForkResult(
//                        String.format(CONTENT_PATH_NOT_IN_DEFAULT_BRANCH_TEMPLATE, searchContentPath));
//            } else {
//                // TODO: check to see if it's got our FROM
//                if (!needsToBeUpdated(content, gitForkBranch)) {
//                    return shouldNotForkResult(
//                            String.format(COULD_NOT_FIND_IMAGE_TO_UPDATE_TEMPLATE,
//                                    gitForkBranch.getImageName(), searchContentPath));
//                }
//            }
//        } catch (InterruptedException e) {
//            log.warn("Couldn't get parent content to check for some reason. Trying to proceed...");
//        }
//        return shouldForkResult();
//    }
//
//    protected boolean needsToBeUpdated(GHContent content, GitForkBranch gitForkBranch) {
//        try (InputStream stream = content.read();
//             InputStreamReader streamR = new InputStreamReader(stream);
//             BufferedReader reader = new BufferedReader(streamR)) {
//            String line;
//            while ( (line = reader.readLine()) != null ) {
//                if (FromInstruction.isFromInstruction(line)) {
//                    FromInstruction fromInstruction = new FromInstruction(line);
//                    if (fromInstruction.hasBaseImage(gitForkBranch.getImageName()) &&
//                            fromInstruction.hasADifferentTag(gitForkBranch.getImageTag())) {
//                        return true;
//                    }
//                }
//            }
//
//        } catch (IOException exception) {
//            exception.printStackTrace();
//        }
//        return false;
//    }
//
    /**
     * Attempts to check to see if this user is the owner of the repo (don't fork your own repo).
     * If we can't tell because of a systemic error, don't attempt to fork (we could perhaps loosen this in the future).
     * If this user is the owner of the repo, do not fork.
     *
     * @param parentRepo parent repo we're checking
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
     */
    protected ShouldForkResult parentIsFork(GHRepository parentRepo) {
        return parentRepo.isFork() ? shouldNotForkResult(REPO_IS_FORK) : shouldForkResult();
    }

    /**
     * Check if parentRepo is archived. We won't be able to update it, so do not fork.
     *
     * @param parentRepo parent repo we're checking
     */
    protected ShouldForkResult parentIsArchived(GHRepository parentRepo) {
        return parentRepo.isArchived() ? shouldNotForkResult(REPO_IS_ARCHIVED) : shouldForkResult();

    }
}
