package com.salesforce.dockerfileimageupdate.model;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class GitForkBranchTest {
    @DataProvider
    public Object[][] imageNameAndExpectedBranch() {
        return new Object[][]{
                {"docker.io/some/container",     "",     "docker.io/some/container"},
                {"127.0.0.1:443/some/container", "",     "127.0.0.1-443/some/container"},
                {"docker.io/some/container",     "123",  "docker.io/some/container-123"},
                {"docker.io/some/container",     "   ",  "docker.io/some/container"},
                {"docker.io/some/container",     null,   "docker.io/some/container"},
        };
    }

    @Test(dataProvider = "imageNameAndExpectedBranch")
    public void testGetBranchNameForImageTagCombos(String imageName, String imageTag, String expectedResult) {
        assertEquals(new GitForkBranch(imageName, imageTag, "").getBranchName(), expectedResult);
    }

    @DataProvider
    public Object[][] imageNameAndSpecifiedBranches() {
        return new Object[][]{
                {"docker.io/some/container",     "",     "blah", "blah"},
                {"127.0.0.1:443/some/container", "",     "test", "test"},
                {"docker.io/some/container",     "123",  "",     "docker.io/some/container-123"},
                {"docker.io/some/container",     "   ",  null,   "docker.io/some/container"},
                {"docker.io/some/container",     null,   "",     "docker.io/some/container"},
        };
    }

    @Test(dataProvider = "imageNameAndSpecifiedBranches")
    public void testGetBranchNameForImageSpecifiedBranchCombos(String imageName, String imageTag, String specifiedBranch, String expectedResult) {
        assertEquals(new GitForkBranch(imageName, imageTag, specifiedBranch).getBranchName(), expectedResult);
    }

    @DataProvider
    public Object[][] sameBranchOrImageNamePrefix() {
        return new Object[][]{
                {"docker.io/some/container",     "",     "blah", "blah",                             true},
                {"127.0.0.1:443/some/container", "",     "test", "test",                             true},
                {"docker.io/some/container",     "123",  "",     "docker.io/some/container-123",     true},
                {"127.0.0.1:443/some/container", "123",     "",  "127.0.0.1-443/some/container-987", true},
                {"docker.io/some/container",     "345",  "",     "docker.io/some/container-123",     true},
                {"docker.io/some/container",     "345",  "",     "docker.io/some/container-432",     true},
                {"docker.io/some/container",     "345",  "",     "docker.io/some/container",         true},
                {"docker.io/some/container  ",   "345",  "",     "docker.io/some/container",         true},
                {"  docker.io/some/container",   "345",  "",     "docker.io/some/container",         true},
                {"  docker.io/some/container",   "345",  "",     "  docker.io/some/container",       true},
                {"  docker.io/some/container",   "345",  "",     "  docker.io/some/container  ",     true},
                {"  docker.io/some/container",   "345",  "",     "docker.io/some/container  ",       true},
                {"docker.io/some/container",     "   ",  null,   "docker.io/some/container",         true},
                {"docker.io/some/container",     null,   "",     "docker.io/some/container",         true},
                {"docker.io/some/container",     "12",   "",     null,                               false},
        };
    }

    @Test(dataProvider = "sameBranchOrImageNamePrefix")
    public void testIsSameBranchOrHasImageNamePrefix(String imageName, String imageTag, String specifiedBranch, String inputBranch, boolean expectedResult) {
        assertEquals(new GitForkBranch(imageName, imageTag, specifiedBranch).isSameBranchOrHasImageNamePrefix(inputBranch), expectedResult);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testIsSameBranchOrHasImageNamePrefix() {
        new GitForkBranch("", "1", "");
    }
}