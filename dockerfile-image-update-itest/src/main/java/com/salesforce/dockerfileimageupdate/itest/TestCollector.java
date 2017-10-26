/*
 * Copyright (c) 2017, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate.itest;

import com.google.common.reflect.ClassPath;
import org.testng.annotations.Factory;

import java.io.IOException;
import java.util.*;

/**
 * Created by afalko, smulakala, minho.park on 7/14/16.
 */
public class TestCollector {

    @Factory
    public Object[] runAllTests() throws IOException, IllegalAccessException, InstantiationException {
        // Any new test file, add it to this list
        // TODO: Can use reflection to automatically add these
        List<Object> tests = new ArrayList<>();

        Set<ClassPath.ClassInfo> allClasses = new TreeSet<>(Comparator.comparing(ClassPath.ClassInfo::getName));

        ClassPath classpath = ClassPath.from(TestCollector.class.getClassLoader());
        allClasses.addAll(classpath.getTopLevelClasses("com.salesforce.dockerfileimageupdate.itest.tests"));
        for (ClassPath.ClassInfo classinfo : allClasses) {
            tests.add(classinfo.load().newInstance());
        }
        return tests.toArray();
    }
}
