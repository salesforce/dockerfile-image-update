/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.subcommands.impl;

import com.salesforce.dockerfileimageupdate.SubCommand;
import com.salesforce.dockerfileimageupdate.model.*;
import com.salesforce.dockerfileimageupdate.process.*;
import com.salesforce.dockerfileimageupdate.storage.ImageTagStore;
import com.salesforce.dockerfileimageupdate.storage.ImageTagStoreContent;
import com.salesforce.dockerfileimageupdate.subcommands.ExecutableWithNamespace;
import com.salesforce.dockerfileimageupdate.utils.Constants;
import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;
import com.salesforce.dockerfileimageupdate.utils.ImageStoreUtil;
import com.salesforce.dockerfileimageupdate.utils.ProcessingErrors;
import com.salesforce.dockerfileimageupdate.utils.PullRequests;
import net.sourceforge.argparse4j.inf.Namespace;
import org.kohsuke.github.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

@SubCommand(help="updates all repositories' Dockerfiles",
        requiredParams = {Constants.STORE})
public class All implements ExecutableWithNamespace {
    private static final Logger log = LoggerFactory.getLogger(All.class);

    private DockerfileGitHubUtil dockerfileGitHubUtil;

    @Override
    public void execute(final Namespace ns, final DockerfileGitHubUtil dockerfileGitHubUtil) throws Exception {
        loadDockerfileGithubUtil(dockerfileGitHubUtil);
        String store = ns.get(Constants.STORE);
        try {
            ImageTagStore imageTagStore = ImageStoreUtil.initializeImageTagStore(this.dockerfileGitHubUtil, store);
            List<ImageTagStoreContent> imageNamesWithTag = imageTagStore.getStoreContent(dockerfileGitHubUtil, store);
            Integer numberOfImagesToProcess = imageNamesWithTag.size();
            List<ProcessingErrors> imagesThatCouldNotBeProcessed = processImagesWithTag(ns, imageNamesWithTag);
            printSummary(imagesThatCouldNotBeProcessed, numberOfImagesToProcess);
        } catch (Exception e) {
            log.error("Encountered issues while initializing the image tag store or getting its contents. Cannot continue. Exception: ", e);
            System.exit(2);
        }
    }

    protected List<ProcessingErrors> processImagesWithTag(Namespace ns, List<ImageTagStoreContent> imageNamesWithTag) {
        Integer gitApiSearchLimit = ns.get(Constants.GIT_API_SEARCH_LIMIT);
        Map<String, Boolean> orgsToIncludeInSearch = new HashMap<>();
        if (ns.get(Constants.GIT_ORG) != null) {
            // If there is a Git org specified, that needs to be included in the search query. In
            // the orgsToIncludeInSearch a true value associated with an org name ensures that
            // the org gets included in the search query.
            orgsToIncludeInSearch.put(ns.get(Constants.GIT_ORG), true);
        }
        Optional<Exception> failureMessage;
        List<ProcessingErrors> imagesThatCouldNotBeProcessed = new LinkedList<>();
        for (ImageTagStoreContent content : imageNamesWithTag) {
            String image = content.getImageName();
            String tag = content.getTag();
            failureMessage = processImageWithTag(image, tag, ns, orgsToIncludeInSearch, gitApiSearchLimit);
            failureMessage.ifPresent(message -> imagesThatCouldNotBeProcessed.add(processErrorMessages(image, tag, Optional.of(message))));
        }
        return imagesThatCouldNotBeProcessed;
    }

    protected Optional<Exception> processImageWithTag(String image, String tag, Namespace ns, Map<String, Boolean> orgsToIncludeInSearch, Integer gitApiSearchLimit) {
        Optional<Exception> failureMessage = Optional.empty();
        try {
            PullRequests pullRequests = getPullRequests();
            GitHubPullRequestSender pullRequestSender = getPullRequestSender(dockerfileGitHubUtil, ns);
            GitForkBranch gitForkBranch = getGitForkBranch(image, tag, ns);
            String filenamesToSearch = ns.get(Constants.FILENAMES_TO_SEARCH);

            log.info("Finding Dockerfiles with the image name {}...", image);
            Optional<List<PagedSearchIterable<GHContent>>> contentsWithImage =
                    this.dockerfileGitHubUtil.findFilesWithImage(image, orgsToIncludeInSearch, gitApiSearchLimit, filenamesToSearch);
            if (contentsWithImage.isPresent()) {
                Iterator<PagedSearchIterable<GHContent>> it = contentsWithImage.get().iterator();
                while (it.hasNext()){
                    try {
                        pullRequests.prepareToCreate(ns, pullRequestSender,
                                it.next(), gitForkBranch, dockerfileGitHubUtil);
                    } catch (IOException e) {
                        log.error("Could not send pull request for image {}.", image);
                        failureMessage = Optional.of(e);
                    }
                }
            }

        } catch (GHException | IOException e){
            log.error("Could not perform Github search for the image {}. Trying to proceed...", image);
            failureMessage = Optional.of(e);
        }
        return failureMessage;
    }

    protected void printSummary(List<ProcessingErrors> imagesThatCouldNotBeProcessed, Integer numberOfImagesToProcess) {
        Integer numberOfImagesFailedToProcess = imagesThatCouldNotBeProcessed.size();
        Integer numberOfImagesSuccessfullyProcessed = numberOfImagesToProcess - numberOfImagesFailedToProcess;
        log.info("The total number of images to process from image tag store: {}", numberOfImagesToProcess);
        log.info("The total number of images that were successfully processed: {}", numberOfImagesSuccessfullyProcessed);
        if (numberOfImagesFailedToProcess > 0) {
            log.warn("The total number of images that failed to be processed: {}. The following list shows the images that could not be processed.", numberOfImagesFailedToProcess);
            imagesThatCouldNotBeProcessed.forEach(imageThatCouldNotBeProcessed -> {
                    if (imageThatCouldNotBeProcessed.getFailure().isPresent()) {
                        log.warn("Image: {}:{}, Exception: {}", imageThatCouldNotBeProcessed.getImageName(), imageThatCouldNotBeProcessed.getTag(), imageThatCouldNotBeProcessed.getFailure());
                    } else {
                        log.warn("Image: {}:{}, Exception: Failure reason not known.", imageThatCouldNotBeProcessed.getImageName(), imageThatCouldNotBeProcessed.getTag());
                    }
                }
            );
        }
    }

    protected ProcessingErrors processErrorMessages(String imageName, String tag, Optional<Exception> failure) {
        return new ProcessingErrors(imageName, tag, failure);
    }

    protected void loadDockerfileGithubUtil(DockerfileGitHubUtil _dockerfileGitHubUtil) {
        dockerfileGitHubUtil = _dockerfileGitHubUtil;
    }

    protected GitHubPullRequestSender getPullRequestSender(DockerfileGitHubUtil dockerfileGitHubUtil, Namespace ns){
        return new GitHubPullRequestSender(dockerfileGitHubUtil, new ForkableRepoValidator(dockerfileGitHubUtil),
                ns.get(Constants.GIT_REPO_EXCLUDES));
    }

    protected GitForkBranch getGitForkBranch(String image, String tag, Namespace ns){
        return new GitForkBranch(image, tag, ns.get(Constants.GIT_BRANCH));
    }

    protected PullRequests getPullRequests(){
        return new PullRequests();
    }
}
