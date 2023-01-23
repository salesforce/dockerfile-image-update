package com.salesforce.dockerfileimageupdate.model;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

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
        assertEquals(new GitForkBranch(imageName, imageTag, "", "Dockerfile,docker-compose").getBranchName(), expectedResult);
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
        assertEquals(new GitForkBranch(imageName, imageTag, specifiedBranch, "Dockerfile,docker-compose").getBranchName(), expectedResult);
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
        assertEquals(new GitForkBranch(imageName, imageTag, specifiedBranch, "Dockerfile,docker-compose").isSameBranchOrHasImageNamePrefix(inputBranch), expectedResult);
    }

    @Test(expectedExceptions = IllegalArgumentException.class)
    public void testIsSameBranchOrHasImageNamePrefix() {
        new GitForkBranch("", "1", "", null);
    }

    @DataProvider
    public Object[][] imageNameSpecifiedBranchAndFilenameSearched() {
        return new Object[][]{
                /*{"docker.io/some/container",     "",     "blah", "dockerfile", "blah"},
                {"127.0.0.1:443/some/container", "",     "test", "dockerfile,docker-compose", "test"},
                {"docker.io/some/container",     "123",  "",     "dockerfile", "docker.io/some/container-123_dockerfile"},
                {"docker.io/some/container",     "123",  "",     "dockerfile,docker-compose", "docker.io/some/container-123"},
                {"docker.io/some/container",     "123",  "",     "docker-compose", "docker.io/some/container-123_dockercompose"},
                {"docker.io/some/container",     "   ",  null,   "abcdef", "docker.io/some/container"},*/
                {"docker.io/some/container",     null,   "",     "docker-compose", "docker.io/some/container_dockercompose"},
        };
    }

    @Test(dataProvider = "imageNameSpecifiedBranchAndFilenameSearched")
    public void testGetBranchNameForFileNamesSearched(String imageName, String imageTag, String specifiedBranch, String filenameSearchedFor, String expectedResult) {
        assertEquals(new GitForkBranch(imageName, imageTag, specifiedBranch, filenameSearchedFor).getBranchName(), expectedResult);
    }
}