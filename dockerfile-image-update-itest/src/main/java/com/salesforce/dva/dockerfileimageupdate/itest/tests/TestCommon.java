package com.salesforce.dva.dockerfileimageupdate.itest.tests;

import com.salesforce.dva.dockerfileimageupdate.githubutils.GithubUtil;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Created by afalko on 10/19/17.
 */
public class TestCommon {
    private static final Logger log = LoggerFactory.getLogger(TestCommon.class);

    public static void initializeRepos(GHOrganization org, List<String> repos, String image,
                                       List<GHRepository> createdRepos, GithubUtil githubUtil) throws Exception {
        for (String s : repos) {
            GHRepository repo = org.createRepository(s)
                    .description("Delete if this exists. If it exists, then an integration test crashed somewhere.")
                    .private_(false)
                    .create();
            repo.createContent("FROM " + image + ":test", "Integration Testing", "Dockerfile");
            createdRepos.add(repo);
            log.info("Initializing {}/{}", org.getLogin(), s);
            githubUtil.tryRetrievingContent(repo, "Dockerfile", repo.getDefaultBranch());
        }
    }
}
