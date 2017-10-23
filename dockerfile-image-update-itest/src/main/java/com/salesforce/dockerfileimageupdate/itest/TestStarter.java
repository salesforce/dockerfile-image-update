/*
 * Copyright (c) 2017, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.itest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.*;
import org.testng.xml.XmlSuite;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

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
        IReporter stdoutReporter = new StdOutReporter();

        TestListenerAdapter tla = new TestListenerAdapter();
        TestNG testNG = new TestNG();
        testNG.setOutputDirectory(TEST_PATH);
        testNG.getReporters().add(stdoutReporter);
        testNG.setTestClasses(new Class[]{TestCollector.class});
        testNG.addListener(tla);
        testNG.run();

        if (testNG.hasFailure()) {
            log.error("Test(s) have failed see output above");
            System.exit(2);
        }
    }

    /**
     * TestNG reporter that will output what our tests produce
     */
    private class StdOutReporter implements IReporter {
        @Override
        public void generateReport(List<XmlSuite> xmlSuites, List<ISuite> suites, String huh) {
            for (ISuite suite : suites) {
                Map<String, ISuiteResult> results = suite.getResults();
                for (String resultName : results.keySet()) {
                    ISuiteResult result = results.get(resultName);
                    log.info("Results for {}", resultName);
                    IResultMap passedConfigs = result.getTestContext().getPassedConfigurations();
                    if (passedConfigs != null) {
                        passedConfigs.getAllResults().forEach(passedConfig -> log.info("{} successful", passedConfig.getMethod()));
                    }
                    IResultMap passedTests = result.getTestContext().getPassedTests();
                    if (passedTests != null) {
                        passedTests.getAllResults().forEach(passedTest -> log.info("{} passed", passedTest.getMethod()));
                    }
                    IResultMap skippedTests = result.getTestContext().getSkippedTests();
                    if (skippedTests != null) {
                        skippedTests.getAllResults().forEach(iTestResult -> log.warn("{} skipped", iTestResult.getMethod()));
                    }
                    IResultMap failedConfigurations = result.getTestContext().getFailedConfigurations();
                    if (failedConfigurations != null) {
                        failedConfigurations.getAllResults().forEach(failedConfig -> {
                            log.error("{} configuration failed", failedConfig.getMethod());
                            log.error(failedConfig.getThrowable().getMessage(), failedConfig.getThrowable());
                        });
                    }
                    IResultMap failedTests = result.getTestContext().getFailedTests();
                    if (failedTests != null) {
                        failedTests.getAllResults().forEach(failedTest -> {
                            if (failedTest.getParameters().length > 0) {
                                log.error("{}({}) failed", failedTest.getMethod(), failedTest.getParameters());
                            } else {
                                log.error("{} failed", failedTest.getMethod());
                            }
                            log.error(failedTest.getThrowable().getMessage(), failedTest.getThrowable());
                        });
                    }
                }
            }
        }
    }
}
