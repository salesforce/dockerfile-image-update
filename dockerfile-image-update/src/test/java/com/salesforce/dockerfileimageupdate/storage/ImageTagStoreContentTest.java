package com.salesforce.dockerfileimageupdate.storage;
import org.testng.annotations.Test;

import static org.mockito.Mockito.spy;
import static org.testng.Assert.assertEquals;

public class ImageTagStoreContentTest {
    @Test
    public void testGetImageName() {
        ImageTagStoreContent imageTagStoreContent = spy(new ImageTagStoreContent("image", "tag"));
        String imageName = imageTagStoreContent.getImageName();
        assertEquals(imageName, "image");
    }

    @Test
    public void testGetTag() {
        ImageTagStoreContent imageTagStoreContent = spy(new ImageTagStoreContent("image", "tag"));
        String imageName = imageTagStoreContent.getTag();
        assertEquals(imageName, "tag");
    }
}
