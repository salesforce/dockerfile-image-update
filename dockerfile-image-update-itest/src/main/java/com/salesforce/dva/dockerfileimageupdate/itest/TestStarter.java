/*
 * Copyright (c) 2017, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dva.dockerfileimageupdate.itest;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.*;
import org.testng.xml.XmlSuite;

import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;

/**
 * Created by afalko, smulakala, minho.park on 7/14/16.
 */
public class TestStarter {
    private final static Logger log = LoggerFactory.getLogger(TestStarter.class);
    private final static String TEST_PATH = "/tmp/test-results/test-results/";
    private final static String TEST_RESULTS_PATH = Paths.get(TEST_PATH,"test-results.xml").toString();

    public static void main(String[] args) {
        TestStarter starter = new TestStarter();
        starter.start();
    }

    public void start() {
        Reporter stdoutReporter = new StdOutReporter {

        }

        TestListenerAdapter tla = new TestListenerAdapter();
        TestNG testNG = new TestNG();
        testNG.setOutputDirectory(TEST_PATH);
        testNG.getReporters().add(stdoutReporter);
        testNG.setTestClasses(new Class[]{TestCollector.class});
        testNG.addListener(tla);
        testNG.run();
        // Log test results
        try (FileInputStream inputStream = new FileInputStream(TEST_RESULTS_PATH)) {
            testNG.
        } catch (Exception e) {
            log.debug("Not able to read test results", e);
        }

        if (testNG.hasFailure()) {
            log.error("Test have failed see output above");
            System.exit(2);
        }
    }

    private class StdOutReporter implements IReporter {
        @Override
        public void generateReport(List<XmlSuite> suites, List<ISuite> suiteInterfaces, String huh) {
            log.info("");
        }
    }
}
