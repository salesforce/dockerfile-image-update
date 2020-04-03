package com.salesforce.dockerfileimageupdate.itest;

import com.google.common.base.Strings;
import org.testng.annotations.Test;

import static com.salesforce.dockerfileimageupdate.itest.MainJarFinder.JAR_NAME_FORMAT;
import static org.testng.Assert.assertEquals;

public class MainJarFinderTest {

    public static final String TEST_VERSION = "test-version";
    MainJarFinder mockJarFinder = new MainJarFinder(new MainJarFinder.Environment() {
        @Override
        public String getVariable(String variable) {
            return TEST_VERSION;
        }
    });

    private static String jarName(String version) {
        return String.format(JAR_NAME_FORMAT, version);
    }

    @Test
    public void testGetJarNameFromEnvironment() {
        MainJarFinder.Environment env = new MainJarFinder.Environment();
        if (Strings.isNullOrEmpty(env.getVariable(MainJarFinder.MVN_VERSION))) {
            assertEquals(MainJarFinder.getName(), jarName(MainJarFinder.DEFAULT_VERSION));
        } else {
            assertEquals(MainJarFinder.getName(), jarName(env.getVariable(MainJarFinder.MVN_VERSION)));
        }
    }

    @Test
    public void testMockedGetJarNameFromEnvironment() {
        assertEquals(mockJarFinder.getJarNameFromEnvironment(), jarName(TEST_VERSION));
    }
}