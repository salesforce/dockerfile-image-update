package com.salesforce.dockerfileimageupdate.repository;

import com.google.common.collect.Multimap;
import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;
import org.kohsuke.github.GHRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitHub {
    private static final Logger log = LoggerFactory.getLogger(DockerfileGitHubUtil.class);

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
