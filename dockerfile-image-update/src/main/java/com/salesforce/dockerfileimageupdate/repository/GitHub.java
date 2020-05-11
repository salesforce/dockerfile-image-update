package com.salesforce.dockerfileimageupdate.repository;

import com.google.common.collect.Multimap;
import org.kohsuke.github.GHRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitHub {
    private static final Logger log = LoggerFactory.getLogger(GitHub.class);

    /**
     * Check to see if we found Dockerfiles that need processing in the provided repository and that it isn't archived
     *
     * @param pathToDockerfilesInParentRepo map of dockerfile repos to process
     * @param parent repository to check
     * @return is the parent in the list, valid, and not archived?
     */
    public static boolean shouldNotProcessDockerfilesInRepo(Multimap<String, String> pathToDockerfilesInParentRepo, GHRepository parent) {
        if (parent == null || !pathToDockerfilesInParentRepo.containsKey(parent.getFullName()) || parent.isArchived()) {
            if (parent != null && parent.isArchived()) {
                log.info("Skipping archived repo: {}", parent.getFullName());
            }
            return true;
        }
        return false;
    }
}
