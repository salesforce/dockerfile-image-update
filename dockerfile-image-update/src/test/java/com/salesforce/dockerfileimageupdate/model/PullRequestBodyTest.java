package com.salesforce.dockerfileimageupdate.model;

import org.testng.annotations.Test;

import static com.salesforce.dockerfileimageupdate.model.PullRequestBody.BODY_TEMPLATE;
import static org.testng.Assert.*;

public class PullRequestBodyTest {

    @Test
    public void testGetBody() {
        String image = "myimage";
        String tag = "tag";
        String imageAndTag = image + ":" + tag;
        assertEquals(PullRequestBody.getBody(image, tag), String.format(BODY_TEMPLATE, image, tag, imageAndTag));
    }
}