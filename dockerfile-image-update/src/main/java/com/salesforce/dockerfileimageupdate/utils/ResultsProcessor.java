package com.salesforce.dockerfileimageupdate.utils;

import org.slf4j.Logger;

import java.io.IOException;
import java.util.List;

/**
 * Processes results of a rur
 */
public class ResultsProcessor {

    public static final String REPOS_SKIPPED_MESSAGE = "List of repos skipped: {}";

    /**
     * Process the results of a run and output for the user
     *
     * @param skippedRepos repos that were skipped during processing
     * @param exceptions exceptions that occurred during processing
     * @param logger logger of the caller
     * @throws IOException throws if there are exceptions
     */
    public static void processResults(List<String> skippedRepos, List<IOException> exceptions, Logger logger) throws IOException {
        if (!skippedRepos.isEmpty()) {
            logger.info(REPOS_SKIPPED_MESSAGE, skippedRepos);
        }

        if (!exceptions.isEmpty()) {
            throw new IOException(String.format("There were %s errors with changing Dockerfiles.", exceptions.size()));
        }
    }
}
