package com.salesforce.dockerfileimageupdate.model;

import org.testng.annotations.Test;

import static com.salesforce.dockerfileimageupdate.model.PullRequestInfo.*;
import static org.testng.Assert.*;

public class PullRequestInfoTest {

    public static final String IMAGE = "myimage";
    public static final String TAG = "tag";
    public static final String TITLE = "title";
    public static final String EXTRA = "extra";

    @Test
    public void testGetBody() {
        String imageAndTag = IMAGE + ":" + TAG;
        PullRequestInfo pullRequestInfo = new PullRequestInfo(TITLE, IMAGE, TAG, null);
        assertEquals(pullRequestInfo.getBody(), String.format(BODY_TEMPLATE, IMAGE, IMAGE, TAG, imageAndTag));
    }

    @Test
    public void testGetBodyWithExtra() {
        String imageAndTag = IMAGE + ":" + TAG;
        PullRequestInfo pullRequestInfo = new PullRequestInfo(TITLE, IMAGE, TAG, EXTRA);
        assertEquals(pullRequestInfo.getBody(), String.format(BODY_TEMPLATE, IMAGE, IMAGE, TAG, imageAndTag) + '\n' + EXTRA);
    }

    @Test
    public void testGetBodyIsOldConstantIfImageAndTagNull() {
        PullRequestInfo pullRequestInfo = new PullRequestInfo(TITLE, null, null, null);
        assertEquals(pullRequestInfo.getBody(), OLD_CONSTANT_BODY);
    }

    @Test
    public void testGetBodyIsOldConstantWithExtraIfExtraSuppliedAndImageAndTagNull() {
        PullRequestInfo pullRequestInfo = new PullRequestInfo(TITLE, null, null, EXTRA);
        assertEquals(pullRequestInfo.getBody(), OLD_CONSTANT_BODY + '\n' + EXTRA);
    }

    @Test
    public void testGetTitle() {
        PullRequestInfo pullRequestInfo = new PullRequestInfo(TITLE, IMAGE, TAG, null);
        assertEquals(pullRequestInfo.getTitle(), TITLE);
    }

    @Test
    public void testGetDefaultTitleIfNull() {
        PullRequestInfo pullRequestInfo = new PullRequestInfo(null, IMAGE, TAG, null);
        assertEquals(pullRequestInfo.getTitle(), DEFAULT_TITLE);
    }

    @Test
    public void testGetDefaultTitleIfEmpty() {
        PullRequestInfo pullRequestInfo = new PullRequestInfo("   ", IMAGE, TAG, null);
        assertEquals(pullRequestInfo.getTitle(), DEFAULT_TITLE);
    }
}
