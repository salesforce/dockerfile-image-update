package com.salesforce.dockerfileimageupdate.storage;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.salesforce.dockerfileimageupdate.utils.*;
import org.kohsuke.github.*;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.*;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public class GitHubJsonStoreTest {
    @DataProvider
    public Object[][] inputStores() throws Exception {
        return new Object[][] {
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
        Set<String> EmptySet = Collections.<String>emptySet();
        when(dockerfileGitHubUtil.tryRetrievingContent(store, "store.json", defaultBranch)).thenReturn(null);
        Set<Map.Entry<String, JsonElement>> actualResult =
                gitHubJsonStore.parseStoreToImagesMap(dockerfileGitHubUtil, "store");
        assertEquals(actualResult, EmptySet);
    }
}