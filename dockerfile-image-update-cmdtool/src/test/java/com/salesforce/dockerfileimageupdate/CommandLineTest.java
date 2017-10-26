/*
 * Copyright (c) 2017, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate;

import com.google.common.reflect.ClassPath;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testng.Assert.assertEquals;

/**
 * Created by afalko on 10/25/17.
 */
public class CommandLineTest {
    @Test
    public void testLoadSubcommands() throws Exception {
        Set<ClassPath.ClassInfo> classes = CommandLine.findSubcommands(CommandLine.getArgumentParser());
        List<String> expectedSubCommands = Arrays.asList("All", "Parent", "Child");
        assertEquals(classes.size(), expectedSubCommands.size());
        classes.forEach(
                classInfo -> assertThat(expectedSubCommands).contains(classInfo.getSimpleName())
        );
    }
}