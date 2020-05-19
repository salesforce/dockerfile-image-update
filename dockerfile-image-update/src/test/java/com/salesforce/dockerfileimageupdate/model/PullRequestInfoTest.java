package com.salesforce.dockerfileimageupdate.model;

import org.testng.annotations.Test;

import static com.salesforce.dockerfileimageupdate.model.PullRequestInfo.*;
import static org.testng.Assert.*;

public class PullRequestInfoTest {

    public static final String IMAGE = "myimage";
    public static final String TAG = "tag";
    public static final String TITLE = "title";

    @Test
    public void testGetBody() {
        String imageAndTag = IMAGE + ":" + TAG;
        PullRequestInfo pullRequestInfo = new PullRequestInfo(TITLE, IMAGE, TAG);
        assertEquals(pullRequestInfo.getBody(), String.format(BODY_TEMPLATE, IMAGE, IMAGE, TAG, imageAndTag));
    }

    @Test
    public void testGetBodyIsOldConstantIfImageAndTagNull() {
        PullRequestInfo pullRequestInfo = new PullRequestInfo(TITLE, null, null);
        assertEquals(pullRequestInfo.getBody(), OLD_CONSTANT_BODY);
    }

    @Test
    public void testGetTitle() {
        PullRequestInfo pullRequestInfo = new PullRequestInfo(TITLE, IMAGE, TAG);
        assertEquals(pullRequestInfo.getTitle(), TITLE);
    }

    @Test
    public void testGetDefaultTitleIfNull() {
        PullRequestInfo pullRequestInfo = new PullRequestInfo(null, IMAGE, TAG);
        assertEquals(pullRequestInfo.getTitle(), DEFAULT_TITLE);
    }

    @Test
    public void testGetDefaultTitleIfEmpty() {
        PullRequestInfo pullRequestInfo = new PullRequestInfo("   ", IMAGE, TAG);
        assertEquals(pullRequestInfo.getTitle(), DEFAULT_TITLE);
    }
}