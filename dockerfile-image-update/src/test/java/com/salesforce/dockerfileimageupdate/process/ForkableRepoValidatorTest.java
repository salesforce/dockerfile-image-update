package com.salesforce.dockerfileimageupdate.process;

import com.salesforce.dockerfileimageupdate.model.GitForkBranch;
import com.salesforce.dockerfileimageupdate.model.ShouldForkResult;
import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;

import static com.salesforce.dockerfileimageupdate.model.ShouldForkResult.shouldForkResult;
import static com.salesforce.dockerfileimageupdate.model.ShouldForkResult.shouldNotForkResult;
import static com.salesforce.dockerfileimageupdate.process.ForkableRepoValidator.*;
import static com.salesforce.dockerfileimageupdate.process.GitHubPullRequestSender.REPO_IS_FORK;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.*;

public class ForkableRepoValidatorTest {

    @DataProvider
    public Object[][] shouldForkData() {
        ShouldForkResult dontForkBecauseFork = shouldNotForkResult(REPO_IS_FORK);
        ShouldForkResult dontForkBecauseArchive = shouldNotForkResult(REPO_IS_ARCHIVED);
        ShouldForkResult dontForkBecauseOwner = shouldNotForkResult(REPO_IS_OWNED_BY_THIS_USER);
        return new Object[][]{
                {shouldForkResult(), shouldForkResult(), shouldForkResult(), shouldForkResult()},
                {dontForkBecauseFork, shouldForkResult(), shouldForkResult(), dontForkBecauseFork},
                {dontForkBecauseFork, dontForkBecauseArchive, dontForkBecauseOwner, dontForkBecauseFork},
                {shouldForkResult(), dontForkBecauseArchive, dontForkBecauseOwner, dontForkBecauseArchive},
                {shouldForkResult(), shouldForkResult(), dontForkBecauseOwner, dontForkBecauseOwner},
        };
    }

    @Test(dataProvider = "shouldForkData")
    public void testShouldFork(ShouldForkResult isForkResult,
                               ShouldForkResult isArchivedResult,
                               ShouldForkResult userIsNotOwnerResult,
                               ShouldForkResult expectedResult) {
        ForkableRepoValidator validator = mock(ForkableRepoValidator.class);

        when(validator.parentIsFork(any())).thenReturn(isForkResult);
        when(validator.parentIsArchived(any())).thenReturn(isArchivedResult);
        when(validator.thisUserIsNotOwner(any())).thenReturn(userIsNotOwnerResult);
        when(validator.shouldFork(any(), any(), any())).thenCallRealMethod();

        assertEquals(validator.shouldFork(mock(GHRepository.class), mock(GHContent.class), mock(GitForkBranch.class)),
                expectedResult);
    }

    @Test
    public void testCantForkThisUserIsNotOwner() throws IOException {
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        GHRepository repo = mock(GHRepository.class);
        ForkableRepoValidator validator = new ForkableRepoValidator(dockerfileGitHubUtil);

        when(dockerfileGitHubUtil.thisUserIsOwner(repo)).thenReturn(true);
        ShouldForkResult shouldForkResult = validator.thisUserIsNotOwner(repo);
        assertFalse(shouldForkResult.isForkable());
        assertEquals(shouldForkResult.getReason(), REPO_IS_OWNED_BY_THIS_USER);
    }

    @Test
    public void testCantForkCouldNotTellIfThisUserIsOwner() throws IOException {
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        GHRepository repo = mock(GHRepository.class);
        ForkableRepoValidator validator = new ForkableRepoValidator(dockerfileGitHubUtil);

        when(dockerfileGitHubUtil.thisUserIsOwner(repo)).thenThrow(new IOException("sad times"));
        ShouldForkResult shouldForkResult = validator.thisUserIsNotOwner(repo);
        assertFalse(shouldForkResult.isForkable());
        assertEquals(shouldForkResult.getReason(), COULD_NOT_CHECK_THIS_USER);
    }

    @Test
    public void testCanForkThisUserIsNotOwner() throws IOException {
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        GHRepository repo = mock(GHRepository.class);
        ForkableRepoValidator validator = new ForkableRepoValidator(dockerfileGitHubUtil);

        when(dockerfileGitHubUtil.thisUserIsOwner(repo)).thenReturn(false);
        ShouldForkResult shouldForkResult = validator.thisUserIsNotOwner(repo);
        assertTrue(shouldForkResult.isForkable());
    }

    @Test
    public void testParentIsForkDoNotForkIt() {
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        GHRepository repo = mock(GHRepository.class);
        ForkableRepoValidator validator = new ForkableRepoValidator(dockerfileGitHubUtil);

        when(repo.isFork()).thenReturn(true);
        ShouldForkResult shouldForkResult = validator.parentIsFork(repo);
        assertFalse(shouldForkResult.isForkable());
        assertEquals(shouldForkResult.getReason(), REPO_IS_FORK);
    }

    @Test
    public void testParentIsNotForkSoForkIt() {
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        GHRepository repo = mock(GHRepository.class);
        ForkableRepoValidator validator = new ForkableRepoValidator(dockerfileGitHubUtil);

        when(repo.isFork()).thenReturn(false);
        ShouldForkResult shouldForkResult = validator.parentIsFork(repo);
        assertTrue(shouldForkResult.isForkable());
    }

    @Test
    public void testParentIsArchivedDoNotForkIt() {
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        GHRepository repo = mock(GHRepository.class);
        ForkableRepoValidator validator = new ForkableRepoValidator(dockerfileGitHubUtil);

        when(repo.isArchived()).thenReturn(true);
        ShouldForkResult shouldForkResult = validator.parentIsArchived(repo);
        assertFalse(shouldForkResult.isForkable());
        assertEquals(shouldForkResult.getReason(), REPO_IS_ARCHIVED);
    }

    @Test
    public void testParentIsNotArchivedSoForkIt() {
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        GHRepository repo = mock(GHRepository.class);
        ForkableRepoValidator validator = new ForkableRepoValidator(dockerfileGitHubUtil);

        when(repo.isArchived()).thenReturn(false);
        ShouldForkResult shouldForkResult = validator.parentIsArchived(repo);
        assertTrue(shouldForkResult.isForkable());
    }
}