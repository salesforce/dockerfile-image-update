package com.salesforce.dockerfileimageupdate.storage;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;
import com.salesforce.dockerfileimageupdate.utils.GitHubUtil;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import org.kohsuke.github.GHBlob;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHMyself;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHTree;
import org.kohsuke.github.GHTreeEntry;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public class GitHubJsonStoreTest {
    @DataProvider
    public Object[][] inputStores() throws Exception {
        return new Object[][]{
                {"{\n  \"images\": {\n" +
                        "    \"test\": \"testingtest\",\n" +
                        "    \"asdfjkl\": \"asdfjkl\",\n" +
                        "    \"7fce8488-31f4-4137-ad68-c19c3b33eebb\": \"manualtest\"\n" +
                        "  }\n" +
                        "}",
                        "test", "newtag",
                        "{\n  \"images\": {\n" +
                                "    \"test\": \"newtag\",\n" +
                                "    \"asdfjkl\": \"asdfjkl\",\n" +
                                "    \"7fce8488-31f4-4137-ad68-c19c3b33eebb\": \"manualtest\"\n" +
                                "  }\n" +
                                "}"},
                {"{\n  \"images\": {\n" +
                        "    \"test\": \"testingtest\",\n" +
                        "    \"asdfjkl\": \"asdfjkl\",\n" +
                        "    \"7fce8488-31f4-4137-ad68-c19c3b33eebb\": \"manualtest\"\n" +
                        "  }\n" +
                        "}",
                        "test", "test",
                        "{\n  \"images\": {\n" +
                                "    \"test\": \"test\",\n" +
                                "    \"asdfjkl\": \"asdfjkl\",\n" +
                                "    \"7fce8488-31f4-4137-ad68-c19c3b33eebb\": \"manualtest\"\n" +
                                "  }\n" +
                                "}"},
                {"{\n  \"images\": {\n" +
                        "    \"test\": \"testingtest\",\n" +
                        "    \"asdfjkl\": \"asdfjkl\",\n" +
                        "    \"7fce8488-31f4-4137-ad68-c19c3b33eebb\": \"manualtest\"\n" +
                        "  }\n" +
                        "}",
                        "newImage", "newtag2",
                        "{\n  \"images\": {\n" +
                                "    \"test\": \"testingtest\",\n" +
                                "    \"asdfjkl\": \"asdfjkl\",\n" +
                                "    \"7fce8488-31f4-4137-ad68-c19c3b33eebb\": \"manualtest\",\n" +
                                "    \"newImage\": \"newtag2\"\n" +
                                "  }\n" +
                                "}"},
                {"{}", "image", "tag",
                        "{\n  \"images\": {\n" +
                                "    \"image\": \"tag\"\n" +
                                "  }\n" +
                                "}"},
                {"{}", "test", "test",
                        "{\n  \"images\": {\n" +
                                "    \"test\": \"test\"\n" +
                                "  }\n" +
                                "}"},
                {"", "image", "tag",
                        "{\n  \"images\": {\n" +
                                "    \"image\": \"tag\"\n" +
                                "  }\n" +
                                "}"},
                {"{\n  \"images\": {\n" +
                        "  }\n" +
                        "}",
                        "image", "tag",
                        "{\n  \"images\": {\n" +
                                "    \"image\": \"tag\"\n" +
                                "  }\n" +
                                "}"}

        };
    }

    @DataProvider
    public Object[][] inputTagStore() {
        return new Object[][]{
                {"{\n  \"images\": {\n" +
                        "    \"test\": \"testingtest\",\n" +
                        "    \"asdfjkl\": \"asdfjkl\",\n" +
                        "    \"7fce8488-31f4-4137-ad68-c19c3b33eebb\": \"manualtest\"\n" +
                        "  }\n" +
                        "}"}
        };
    }

    @Test(dataProvider = "inputStores")
    public void testGetAndModifyJsonString(String storeContent, String image, String tag, String expectedOutput) throws Exception {
        GitHubUtil gitHubUtil = mock(GitHubUtil.class);
        JsonElement json = JsonParser.parseString(storeContent);

        String output = new GitHubJsonStore(gitHubUtil, "test").getAndModifyJsonString(json, image, tag);
        assertEquals(output, expectedOutput);
    }

    @Test
    public void testParseStoreToImagesMap() throws Exception {
        GitHubJsonStore gitHubJsonStore = mock(GitHubJsonStore.class);
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        GHMyself ghMyself = mock(GHMyself.class);
        when(dockerfileGitHubUtil.getMyself()).thenReturn(ghMyself);
        String dummyLogin = "dummy_login";
        when(ghMyself.getLogin()).thenReturn(dummyLogin);
        GHRepository store = mock(GHRepository.class);
        when(dockerfileGitHubUtil.getRepo(anyString())).thenReturn(store);
        String defaultBranch = "default-branch";
        when(store.getDefaultBranch()).thenReturn(defaultBranch);
        Set<String> EmptySet = Collections.emptySet();
        when(dockerfileGitHubUtil.tryRetrievingContent(store, "store.json", defaultBranch)).thenReturn(null);
        Set<Map.Entry<String, JsonElement>> actualResult =
                gitHubJsonStore.parseStoreToImagesMap(dockerfileGitHubUtil, "store");
        assertEquals(actualResult, EmptySet);
    }


    @Test
    public void testInitializeTagStoreIfRequired() throws IOException {
        GHRepository repo = mock(GHRepository.class);
        GitHubUtil gitHubUtil = mock(GitHubUtil.class);
        GitHubJsonStore gitHubJsonStore = new GitHubJsonStore(gitHubUtil, "test");
        when(repo.getFileContent(anyString())).thenThrow(new IOException("file sized is too large. Error:  too_large"));
        gitHubJsonStore.initializeTagStoreIfRequired(repo, "store.json");
    }

    @Test(
            expectedExceptions = IOException.class,
            expectedExceptionsMessageRegExp = "error while reading file from scm"
    )
    public void testInitializeTagStoreIfRequiredExceptionThrown() throws IOException {
        GHRepository repo = mock(GHRepository.class);
        GitHubUtil gitHubUtil = mock(GitHubUtil.class);
        GitHubJsonStore gitHubJsonStore = new GitHubJsonStore(gitHubUtil, "test");
        when(repo.getFileContent(anyString())).thenThrow(new IOException("error while reading file from scm"));
        gitHubJsonStore.initializeTagStoreIfRequired(repo, "store.json");
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testInitializeTagStoreIfRequiredSuccess() throws IOException {
        GHRepository repo = mock(GHRepository.class);
        GitHubUtil gitHubUtil = mock(GitHubUtil.class);
        GitHubJsonStore gitHubJsonStore = new GitHubJsonStore(gitHubUtil, "test");
        when(repo.getFileContent(anyString())).thenThrow(new FileNotFoundException(""));
        gitHubJsonStore.initializeTagStoreIfRequired(repo, "store.json");
    }


    @Test(dataProvider = "inputTagStore",
            expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = "error while creating commit to the git tree")
    public void testUpdateStoreOnGithubCommitFailure(String storeContent) throws IOException {
        GitHubUtil gitHubUtil = mock(GitHubUtil.class);
        GHRepository repo = mock(GHRepository.class);
        GHCommit ghCommit = mock(GHCommit.class);
        GHBlob ghBlob = mock(GHBlob.class);
        GHTree ghTree = mock(GHTree.class);
        GHTreeEntry ghTreeEntry = mock(GHTreeEntry.class);

        when(repo.getDefaultBranch()).thenReturn("defaultBranch");
        when(repo.getFileContent(anyString())).thenThrow(new IOException("file sized is too large. Error:  too_large"));
        when(repo.getCommit(anyString())).thenReturn(ghCommit);
        when(ghCommit.getTree()).thenReturn(ghTree);
        when(ghTree.getEntry(anyString())).thenReturn(ghTreeEntry);
        when(ghTreeEntry.asBlob()).thenReturn(ghBlob);
        when(ghBlob.read()).thenReturn(new ByteArrayInputStream(storeContent.getBytes(StandardCharsets.UTF_8)));
        when(repo.createTree()).thenThrow(new RuntimeException("error while creating commit to the git tree"));

        GitHubJsonStore gitHubJsonStore = new GitHubJsonStore(gitHubUtil, "test");
        gitHubJsonStore.updateStoreOnGithub(repo, "path", "imag", "tag");
    }

    @Test(
            expectedExceptions = RuntimeException.class,
            expectedExceptionsMessageRegExp = "error while creating commit to the git tree")
    public void testUpdateStoreOnGithubInvalidJson() throws IOException {
        GitHubUtil gitHubUtil = mock(GitHubUtil.class);
        GHRepository repo = mock(GHRepository.class);
        GHCommit ghCommit = mock(GHCommit.class);
        GHBlob ghBlob = mock(GHBlob.class);
        GHTree ghTree = mock(GHTree.class);
        GHTreeEntry ghTreeEntry = mock(GHTreeEntry.class);

        when(repo.getDefaultBranch()).thenReturn("defaultBranch");
        when(repo.getFileContent(anyString())).thenThrow(new IOException("file sized is too large. Error:  too_large"));
        when(repo.getCommit(anyString())).thenReturn(ghCommit);
        when(ghCommit.getTree()).thenReturn(ghTree);
        when(ghTree.getEntry(anyString())).thenReturn(ghTreeEntry);
        when(ghTreeEntry.asBlob()).thenReturn(ghBlob);
        when(ghBlob.read()).thenReturn(new ByteArrayInputStream("random invalid json string".getBytes(StandardCharsets.UTF_8)));
        when(repo.createTree()).thenThrow(new RuntimeException("error while creating commit to the git tree"));

        GitHubJsonStore gitHubJsonStore = new GitHubJsonStore(gitHubUtil, "test");
        gitHubJsonStore.updateStoreOnGithub(repo, "path", "imag", "tag");
    }


}
