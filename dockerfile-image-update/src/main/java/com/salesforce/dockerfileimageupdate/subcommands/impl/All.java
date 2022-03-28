/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.subcommands.impl;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.google.gson.JsonElement;
import com.salesforce.dockerfileimageupdate.SubCommand;
import com.salesforce.dockerfileimageupdate.model.*;
import com.salesforce.dockerfileimageupdate.process.*;
import com.salesforce.dockerfileimageupdate.storage.S3Store;
import com.salesforce.dockerfileimageupdate.subcommands.ExecutableWithNamespace;
import com.salesforce.dockerfileimageupdate.utils.*;
import net.sourceforge.argparse4j.inf.Namespace;
import org.kohsuke.github.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.text.html.Option;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@SubCommand(help="updates all repositories' Dockerfiles",
        requiredParams = {Constants.STORE})
public class All implements ExecutableWithNamespace {
    private static final Logger log = LoggerFactory.getLogger(All.class);
    private static final String s3Prefix = "s3://";

    private DockerfileGitHubUtil dockerfileGitHubUtil;
    DockerfileS3Util dockerfileS3Util;

    @Override
    public void execute(final Namespace ns, final DockerfileGitHubUtil dockerfileGitHubUtil, DockerfileS3Util dockerfileS3Util)
            throws IOException, InterruptedException {
        loadDockerfileGithubUtil(dockerfileGitHubUtil);
        loadDockerfileS3Util(dockerfileS3Util);
        Integer gitApiSearchLimit = ns.get(Constants.GIT_API_SEARCH_LIMIT);
        Map<String, Boolean> orgsToIncludeInSearch = new HashMap<>();
        if (ns.get(Constants.GIT_ORG) != null) {
            // If there is a Git org specified, that needs to be included in the search query. In
            // the orgsToIncludeInSearch a true value associated with an org name ensures that
            // the org gets included in the search query.
            orgsToIncludeInSearch.put(ns.get(Constants.GIT_ORG), true);
        }
        List<ProcessingErrors> imagesThatCouldNotBeProcessed = new LinkedList<>();
        AtomicInteger numberOfImagesToProcess = new AtomicInteger();
        String store = ns.get(Constants.STORE);
        Optional<Exception> failureMessage;
        if (store.startsWith(s3Prefix)) {
            log.info("The underlying data store is an S3 bucket.");
            S3Store s3Store =  this.dockerfileS3Util.getS3ImageStore(store);
            ListObjectsV2Result result = s3Store.getS3Objects();
            List<S3ObjectSummary> objects = result.getObjectSummaries();
            numberOfImagesToProcess.set(objects.size());
            for (S3ObjectSummary os : objects) {
                String key = os.getKey();
                String image = s3Store.convertS3ObjectKeyToImageString(key);
                S3Object o = this.dockerfileS3Util.getS3BucketUtil().getS3Client().getObject(store, key);
                String tag = o.getObjectContent().toString();
                failureMessage = processImageWithTag(image, tag, ns, orgsToIncludeInSearch, gitApiSearchLimit);
                if (failureMessage.isPresent()) {
                    ProcessingErrors processingErrors = processErrorMessages(image, tag, failureMessage);
                    imagesThatCouldNotBeProcessed.add(processingErrors);
                }
            }
        } else {
            log.info("The underlying data store is a json store on a Git repository.");
            Set<Map.Entry<String, JsonElement>> imageToTagStore =
                    this.dockerfileGitHubUtil.getGitHubJsonStore(store).parseStoreToImagesMap(dockerfileGitHubUtil, store);
            numberOfImagesToProcess.set(imageToTagStore.size());
            for (Map.Entry<String, JsonElement> imageToTag : imageToTagStore) {
                String image = imageToTag.getKey();
                String tag = imageToTag.getValue().getAsString();
                failureMessage = processImageWithTag(image, tag, ns, orgsToIncludeInSearch, gitApiSearchLimit);
                if (failureMessage.isPresent()) {
                    ProcessingErrors processingErrors = processErrorMessages(image, tag, failureMessage);
                    imagesThatCouldNotBeProcessed.add(processingErrors);
                }
            }
        }
        printSummary(imagesThatCouldNotBeProcessed, numberOfImagesToProcess);
    }

    protected Optional<Exception> processImageWithTag(String image, String tag, Namespace ns, Map<String, Boolean> orgsToIncludeInSearch, Integer gitApiSearchLimit) {
        Optional<Exception> failureMessage = Optional.empty();
        try {
            PullRequests pullRequests = getPullRequests();
            GitHubPullRequestSender pullRequestSender = getPullRequestSender(dockerfileGitHubUtil, ns);
            GitForkBranch gitForkBranch = getGitForkBranch(image, tag, ns);

            log.info("Finding Dockerfiles with the image name {}...", image);
            Optional<List<PagedSearchIterable<GHContent>>> contentsWithImage =
                    this.dockerfileGitHubUtil.findFilesWithImage(image, orgsToIncludeInSearch, gitApiSearchLimit);
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

    protected void printSummary(List<ProcessingErrors> imagesThatCouldNotBeProcessed, AtomicInteger numberOfImagesToProcess) {
        AtomicInteger numberOfImagesFailedToProcess = new AtomicInteger(imagesThatCouldNotBeProcessed.size());
        AtomicInteger numberOfImagesSuccessfullyProcessed = new AtomicInteger(numberOfImagesToProcess.get() - numberOfImagesFailedToProcess.get());
        log.info("The total number of images to process from image tag store: {}", numberOfImagesToProcess.get());
        log.info("The total number of images that were successfully processed: {}", numberOfImagesSuccessfullyProcessed.get());
        if (numberOfImagesFailedToProcess.get() > 0) {
            log.warn("The total number of images that failed to be processed: {}. The following list shows the images that could not be processed.", numberOfImagesFailedToProcess.get());
            imagesThatCouldNotBeProcessed.forEach(imageThatCouldNotBeProcessed -> {
                    if (imageThatCouldNotBeProcessed.getFailure().isPresent()) {
                        log.warn("Image: {}, Exception: {}", imageThatCouldNotBeProcessed.getImageNameAndTag(), imageThatCouldNotBeProcessed.getFailure());
                    } else {
                        log.warn("Image: {}, Exception: {}", imageThatCouldNotBeProcessed.getImageNameAndTag(), "Failure reason not known.");
                    }
                }
            );
        }
    }

    protected ProcessingErrors processErrorMessages(String image, String tag, Optional<Exception> failure) {
        String imageNameAndTag = image + ":" + tag;
        return new ProcessingErrors(imageNameAndTag, failure);
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

    protected void loadDockerfileS3Util(DockerfileS3Util _dockerfileS3UtilUtil) {
        dockerfileS3Util = _dockerfileS3UtilUtil;
    }
}
