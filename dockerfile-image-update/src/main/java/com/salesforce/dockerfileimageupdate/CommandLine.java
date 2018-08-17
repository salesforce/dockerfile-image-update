/*
 * Copyright (c) 2017, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate;


import com.google.common.reflect.ClassPath;
import com.salesforce.dockerfileimageupdate.subcommands.ExecutableWithNamespace;
import com.salesforce.dockerfileimageupdate.utils.Constants;
import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;
import com.salesforce.dockerfileimageupdate.utils.GitHubUtil;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.*;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Created by minho-park on 6/29/2016.
 */
public class CommandLine {
    private static final Logger log = LoggerFactory.getLogger(CommandLine.class);

    /* Should never actually be instantiated in code */
    private CommandLine () { }

    public static void main(String[] args)
            throws IOException, IllegalAccessException, InstantiationException, InterruptedException {
        ArgumentParser parser = getArgumentParser();

        Set<ClassPath.ClassInfo> allClasses = findSubcommands(parser);
        Namespace ns = handleArguments(parser, args);
        if (ns == null)
            System.exit(1);
        Class<?> runClass = loadCommand(allClasses, ns.get(Constants.COMMAND));
        DockerfileGitHubUtil dockerfileGitHubUtil = initializeDockerfileGithubUtil(ns.get(Constants.GIT_API));

        /* Execute given command. */
        ((ExecutableWithNamespace)runClass.newInstance()).execute(ns, dockerfileGitHubUtil);
    }

    static ArgumentParser getArgumentParser() {
        ArgumentParser parser = ArgumentParsers.newArgumentParser("dockerfile-image-update", true)
                .description("Image Updates through Pull Request Automator");

        parser.addArgument("-o", "--" + Constants.GIT_ORG)
                .help("search within specific organization (default: all of github)");
        /* Currently, because of argument passing reasons, you can only specify one branch. */
        parser.addArgument("-b", "--" + Constants.GIT_BRANCH)
                .help("make pull requests for given branch name (default: master)");
        parser.addArgument("-g", "--" + Constants.GIT_API)
                .help("link to github api; overrides environment variable");
        parser.addArgument("-f", "--auto-merge").action(Arguments.storeTrue())
                .help("NOT IMPLEMENTED / set to automatically merge pull requests if available");
        parser.addArgument("-m")
                .help("message to provide for pull requests");
        parser.addArgument("-c")
                .help("additional commit message for the commits in pull requests");
        return parser;
    }

    /*  Adding subcommands to the subcommands list.
            argparse4j allows commands to be truncated, so users can type the first letter (a,c,p) for commands */
    public static Set<ClassPath.ClassInfo> findSubcommands(ArgumentParser parser) throws IOException {
        Subparsers subparsers = parser.addSubparsers()
                .dest(Constants.COMMAND)
                .help("FEATURE")
                .title("subcommands")
                .description("Specify which feature to perform")
                .metavar("COMMAND");

        Set<ClassPath.ClassInfo> allClasses = new TreeSet<>(Comparator.comparing(ClassPath.ClassInfo::getName));
        ClassPath classpath = ClassPath.from(CommandLine.class.getClassLoader());
        allClasses.addAll(classpath.getTopLevelClasses("com.salesforce.dockerfileimageupdate.subcommands.impl"));
        allClasses = allClasses.stream()
                .filter(classInfo -> !classInfo.getName().endsWith("Test"))
                .collect(Collectors.toSet());

        for (ClassPath.ClassInfo classInfo : allClasses) {
            handleAnnotations(classInfo, subparsers);
        }
        return allClasses;
    }

    /* Looks at SubCommand annotation to pull information about each command. */
    public static void handleAnnotations(ClassPath.ClassInfo classInfo, Subparsers subparsers) throws IOException {
        Class<?> clazz = classInfo.load();
        if (clazz.isAnnotationPresent(SubCommand.class)) {
            SubCommand subCommand = clazz.getAnnotation(SubCommand.class);
            if (subCommand.ignore()) {
                return;
            }
            Subparser subparser = subparsers.addParser(classInfo.getSimpleName().toLowerCase()).help(subCommand.help());
            for (String requiredArg : subCommand.requiredParams()) {
                if (requiredArg.isEmpty()) {
                    continue;
                }
                subparser.addArgument(requiredArg).required(true).help("REQUIRED");
            }
            boolean tag = true;
            Argument arg = null;
            for (String optionalArg : subCommand.optionalParams()) {
                if (optionalArg.isEmpty()) {
                    continue;
                }
                if (tag) {
                    arg = subparser.addArgument("-" + optionalArg).help("OPTIONAL");
                    tag = false;
                } else {
                    arg.dest(optionalArg);
                    tag = true;
                }
            }

        } else {
            throw new IOException("There is a command without annotation: " + clazz.getSimpleName());
        }
    }

    /* Checks if the args passed into the command line is valid. */
    public static Namespace handleArguments(ArgumentParser parser, String[] args) {
        Namespace ns = null;
        try {
            ns = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
        }
        return ns;
    }

    /* Find command using reflection. */
    public static Class<?> loadCommand(Set<ClassPath.ClassInfo> allClasses, String command) throws IOException {
        Class<?> runClass = null;
        for (ClassPath.ClassInfo classInfo : allClasses) {
            if (classInfo.getSimpleName().equalsIgnoreCase(command)) {
                runClass = classInfo.load();
            }
        }
        if (runClass == null) {
            throw new IOException("FATAL: Could not execute command.");
        }
        return runClass;
    }

    /* Validate API URL and connect to the API using credentials. */
    public static DockerfileGitHubUtil initializeDockerfileGithubUtil(String gitApiUrl) throws IOException {
        if (gitApiUrl == null) {
            gitApiUrl = System.getenv("git_api_url");
            if (gitApiUrl == null) {
                throw new IOException("No Git API URL in environment variables.");
            }
        }
        String token = System.getenv("git_api_token");
        if (token == null) {
            log.error("Please provide GitHub token in environment variables.");
            System.exit(3);
        }

        GitHub github = new GitHubBuilder().withEndpoint(gitApiUrl)
                .withOAuthToken(token)
                .build();
        github.checkApiUrlValidity();

        GitHubUtil gitHubUtil = new GitHubUtil(github);

        return new DockerfileGitHubUtil(gitHubUtil);
    }
}
