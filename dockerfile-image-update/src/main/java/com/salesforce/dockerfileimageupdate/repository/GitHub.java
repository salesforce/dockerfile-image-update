package com.salesforce.dockerfileimageupdate.repository;

import com.google.common.collect.Multimap;
import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

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

    /**
     * Check to see if latest sha from parent is in the tree of the fork. If not, something has happened in GitHub to
     * make the commit graph inconsistent. We'll just call it "stale" for brevity. GitHub Enterprise administrators
     * can fix this by running "Rebuild Network Graph" in the administrative user interface. Deleting the fork can work,
     * but that results in long delays since the API thinks the fork is still there after deletion for some long period
     * of time.
     * If we administrators don't rebuild the network graph, we get a "422 Reference update failed" if we try and
     * create a new branch. https://docs.github.com/enterprise/2.22/rest/reference/git#create-a-reference
     * Updating the default branch of the fork to the sha results in "422 Object does not exist"
     * https://docs.github.com/enterprise/2.22/rest/reference/git#update-a-reference
     *
     * @param parent the parent
     * @param fork the fork associated with parent
     * @return the fork is stale for some reason
     * @throws IOException general issues trying to get information about the parent's branch will throw an IOException
     */
    public static boolean isForkStale(GHRepository parent, GHRepository fork) throws IOException {
        String sha1 = parent.getBranch(parent.getDefaultBranch()).getSHA1();
        try {
            fork.getTree(sha1);
        } catch (GHFileNotFoundException e) {
            log.info("fork {} is stale... A GitHub administrator will need to rebuild the network graph.",
                    fork.getFullName());
            return true;
        }
        return false;
    }
}
