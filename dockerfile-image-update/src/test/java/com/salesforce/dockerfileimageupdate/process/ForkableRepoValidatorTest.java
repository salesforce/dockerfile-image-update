package com.salesforce.dockerfileimageupdate.process;

import com.salesforce.dockerfileimageupdate.model.GitForkBranch;
import com.salesforce.dockerfileimageupdate.model.ShouldForkResult;
import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;
import org.kohsuke.github.GHContent;
import org.kohsuke.github.GHRepository;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static com.salesforce.dockerfileimageupdate.model.ShouldForkResult.shouldForkResult;
import static com.salesforce.dockerfileimageupdate.model.ShouldForkResult.shouldNotForkResult;
import static com.salesforce.dockerfileimageupdate.process.ForkableRepoValidator.*;
import static com.salesforce.dockerfileimageupdate.process.GitHubPullRequestSender.REPO_IS_FORK;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.*;

public class ForkableRepoValidatorTest {

    @DataProvider
    public Object[][] shouldForkData() {
        ShouldForkResult dontForkBecauseFork = shouldNotForkResult(REPO_IS_FORK);
        ShouldForkResult dontForkBecauseArchive = shouldNotForkResult(REPO_IS_ARCHIVED);
        ShouldForkResult dontForkBecauseOwner = shouldNotForkResult(REPO_IS_OWNED_BY_THIS_USER);
        ShouldForkResult dontForkNoContentChange = shouldNotForkResult("stub no content change message");
        return new Object[][]{
                {shouldForkResult(), shouldForkResult(), shouldForkResult(), shouldForkResult(), shouldForkResult()},
                {dontForkBecauseFork, shouldForkResult(), shouldForkResult(), shouldForkResult(), dontForkBecauseFork},
                {dontForkBecauseFork, dontForkBecauseArchive, dontForkBecauseOwner, shouldForkResult(), dontForkBecauseFork},
                {shouldForkResult(), dontForkBecauseArchive, dontForkBecauseOwner, shouldForkResult(), dontForkBecauseArchive},
                {shouldForkResult(), shouldForkResult(), dontForkBecauseOwner, shouldForkResult(), dontForkBecauseOwner},
                {shouldForkResult(), shouldForkResult(), shouldForkResult(), dontForkNoContentChange, dontForkNoContentChange},
        };
    }

    @Test(dataProvider = "shouldForkData")
    public void testShouldFork(ShouldForkResult isForkResult,
                               ShouldForkResult isArchivedResult,
                               ShouldForkResult userIsNotOwnerResult,
                               ShouldForkResult contentHasChangesResult,
                               ShouldForkResult expectedResult) {
        ForkableRepoValidator validator = mock(ForkableRepoValidator.class);

        when(validator.parentIsFork(any())).thenReturn(isForkResult);
        when(validator.parentIsArchived(any())).thenReturn(isArchivedResult);
        when(validator.thisUserIsNotOwner(any())).thenReturn(userIsNotOwnerResult);
        when(validator.contentHasChangesInDefaultBranch(any(), any(), any())).thenReturn(contentHasChangesResult);
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

    @Test
    public void testContentHasChangesInDefaultBranch() throws InterruptedException, IOException {
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        GHRepository repo = mock(GHRepository.class);
        ForkableRepoValidator validator = new ForkableRepoValidator(dockerfileGitHubUtil);
        GHContent content = mock(GHContent.class);
        when(content.getPath()).thenReturn("/Dockerfile");
        GitForkBranch gitForkBranch = new GitForkBranch("someImage", "someTag", null);
        when(dockerfileGitHubUtil.tryRetrievingContent(eq(repo), any(), any())).thenReturn(content);
        InputStream inputStream = new ByteArrayInputStream("FROM someImage".getBytes());
        when(content.read()).thenReturn(inputStream);

        assertEquals(validator.contentHasChangesInDefaultBranch(repo, content, gitForkBranch), shouldForkResult());
    }

    @Test
    public void testInterruptedExceptionWhenRetrievingContentInDefaultBranch() throws InterruptedException {
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        GHRepository repo = mock(GHRepository.class);
        ForkableRepoValidator validator = new ForkableRepoValidator(dockerfileGitHubUtil);
        GHContent content = mock(GHContent.class);
        when(content.getPath()).thenReturn("/Dockerfile");
        GitForkBranch gitForkBranch = new GitForkBranch("someImage", "someTag", null);
        when(dockerfileGitHubUtil.tryRetrievingContent(eq(repo), any(), any())).thenThrow(new InterruptedException("some exception"));

        assertEquals(validator.contentHasChangesInDefaultBranch(repo, content, gitForkBranch), shouldForkResult());
    }

    @Test
    public void testContentHasNoChangesInDefaultBranch() throws InterruptedException, IOException {
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        GHRepository repo = mock(GHRepository.class);
        ForkableRepoValidator validator = new ForkableRepoValidator(dockerfileGitHubUtil);
        GHContent content = mock(GHContent.class);
        String searchContentPath = "/Dockerfile";
        when(content.getPath()).thenReturn(searchContentPath);
        String imageName = "someImage";
        GitForkBranch gitForkBranch = new GitForkBranch(imageName, "someTag", null);
        when(dockerfileGitHubUtil.tryRetrievingContent(eq(repo), any(), any())).thenReturn(content);
        InputStream inputStream = new ByteArrayInputStream("nochanges".getBytes());
        when(content.read()).thenReturn(inputStream);

        assertEquals(validator.contentHasChangesInDefaultBranch(repo, content, gitForkBranch),
                shouldNotForkResult(String.format(COULD_NOT_FIND_IMAGE_TO_UPDATE_TEMPLATE, imageName, searchContentPath)));
    }

    @Test
    public void testCouldNotFindContentInDefaultBranch() throws InterruptedException {
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        GHRepository repo = mock(GHRepository.class);
        ForkableRepoValidator validator = new ForkableRepoValidator(dockerfileGitHubUtil);
        GHContent content = mock(GHContent.class);
        String searchContentPath = "/Dockerfile";
        when(content.getPath()).thenReturn(searchContentPath);
        GitForkBranch gitForkBranch = new GitForkBranch("someImage", "someTag", null);
        when(dockerfileGitHubUtil.tryRetrievingContent(eq(repo), any(), any())).thenReturn(null);
        InputStream inputStream = new ByteArrayInputStream("FROM someImage".getBytes());

        assertEquals(validator.contentHasChangesInDefaultBranch(repo, content, gitForkBranch),
                shouldNotForkResult(String.format(CONTENT_PATH_NOT_IN_DEFAULT_BRANCH_TEMPLATE, searchContentPath)));
    }

    @DataProvider
    public Object[][] hasNoChangesData() {
        return new Object[][]{
                {"something", "imageName", "tag", true},
                {"FROM imageName", "imageName", "tag", false},
                {"FROM imageName:tag", "imageName", "tag", true},
                {"FROM imageName:anotherTag", "imageName", "tag", false},
                {"FROM anotherImage:tag", "imageName", "tag", true},
        };
    }

    @Test(dataProvider = "hasNoChangesData")
    public void testHasNoChanges(String contentText, String imageName, String imageTag, boolean expectedResult) throws IOException {
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        GHRepository repo = mock(GHRepository.class);
        ForkableRepoValidator validator = new ForkableRepoValidator(dockerfileGitHubUtil);
        GHContent content = mock(GHContent.class);
        InputStream inputStream = new ByteArrayInputStream(contentText.getBytes());
        GitForkBranch gitForkBranch = new GitForkBranch(imageName, imageTag, null);

        when(content.read()).thenReturn(inputStream);

        when(repo.isArchived()).thenReturn(false);
        assertEquals(validator.hasNoChanges(content, gitForkBranch), expectedResult);
    }

    @Test
    public void testHasNoChangesIfExceptionThrownDuringRead() throws IOException {
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        GHRepository repo = mock(GHRepository.class);
        ForkableRepoValidator validator = new ForkableRepoValidator(dockerfileGitHubUtil);
        GHContent content = mock(GHContent.class);
        GitForkBranch gitForkBranch = new GitForkBranch("name", "tag", null);

        when(content.read()).thenThrow(new IOException("failed on IO"));

        assertTrue(validator.hasNoChanges(content, gitForkBranch));
    }
}