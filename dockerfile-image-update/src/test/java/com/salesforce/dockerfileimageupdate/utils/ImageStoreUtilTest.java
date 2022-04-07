package com.salesforce.dockerfileimageupdate.utils;

import com.salesforce.dockerfileimageupdate.storage.GitHubJsonStore;
import com.salesforce.dockerfileimageupdate.storage.ImageTagStore;
import com.salesforce.dockerfileimageupdate.storage.S3BackedImageTagStore;
import org.testng.annotations.Test;

import static org.mockito.Mockito.*;
import static org.testng.Assert.assertEquals;

public class ImageStoreUtilTest {

    @Test
    public void testInitializeImageStoreForGitBasedStore() throws Exception {
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        GitHubJsonStore gitHubJsonStore = mock(GitHubJsonStore.class);
        when(dockerfileGitHubUtil.getGitHubJsonStore("store")).thenReturn(gitHubJsonStore);

        ImageTagStore imageTagStore = ImageStoreUtil.initializeImageTagStore(dockerfileGitHubUtil, "store");
        verify(dockerfileGitHubUtil).getGitHubJsonStore("store");
        assertEquals(imageTagStore.getClass(), gitHubJsonStore.getClass());
    }

    @Test
    public void testInitializeImageStoreForS3BasedStore() throws Exception {
        DockerfileGitHubUtil dockerfileGitHubUtil = mock(DockerfileGitHubUtil.class);
        ImageTagStore imageTagStore = ImageStoreUtil.initializeImageTagStore(dockerfileGitHubUtil, "s3://store");
        assertEquals(imageTagStore.getClass(), S3BackedImageTagStore.class);
    }
}
