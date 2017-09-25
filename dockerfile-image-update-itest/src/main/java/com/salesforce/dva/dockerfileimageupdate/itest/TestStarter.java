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
import org.testng.TestListenerAdapter;
import org.testng.TestNG;

import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

/**
 * Created by afalko, smulakala, minho.park on 7/14/16.
 */
public class TestStarter {
    private final static Logger logger = LoggerFactory.getLogger(TestStarter.class);
    private final static String TEST_PATH = "/tmp/test-results/test-results/";
    private final static String TEST_RESULTS_PATH = TEST_PATH + "test-results.xml";

    public static void main(String[] args) {
        TestStarter starter = new TestStarter();
        starter.start();
    }

    public void start() {
        TestListenerAdapter tla = new TestListenerAdapter();
        TestNG testNG = new TestNG();
        testNG.setOutputDirectory(TEST_PATH);
        testNG.setTestClasses(new Class[]{TestCollector.class});
        testNG.addListener(tla);
        testNG.run();
        // Log test results
        try (FileInputStream inputStream = new FileInputStream(TEST_RESULTS_PATH)) {
            logger.info(IOUtils.toString(inputStream, StandardCharsets.UTF_8));
        } catch (Exception e) {
            logger.debug("Not able to read test results", e);
        }
    }

}
