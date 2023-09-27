/*
 * Copyright (c) 2018, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.txt file in the repo root or
 * https://opensource.org/licenses/BSD-3-Clause
 */

package com.salesforce.dockerfileimageupdate;

import com.google.common.reflect.ClassPath;
import com.salesforce.dockerfileimageupdate.subcommands.ExecutableWithNamespace;
import com.salesforce.dockerfileimageupdate.utils.DockerfileGitHubUtil;
import com.salesforce.dockerfileimageupdate.utils.GitHubUtil;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.*;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;

import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.extras.okhttp3.OkHttpGitHubConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import static com.salesforce.dockerfileimageupdate.utils.Constants.*;

/**
 * Created by minho-park on 6/29/2016.
 */
public class CommandLine {
    private static final Logger log = LoggerFactory.getLogger(CommandLine.class);

    /* Should never actually be instantiated in code */
    private CommandLine () { }

    public static void main(String[] args)
            throws Exception {
        ArgumentParser parser = getArgumentParser();

        Set<ClassPath.ClassInfo> allClasses = findSubcommands(parser);
        Namespace ns = handleArguments(parser, args);
        if (ns == null)
            System.exit(1);
        Class<?> runClass = loadCommand(allClasses, ns.get(COMMAND));
        DockerfileGitHubUtil dockerfileGitHubUtil = initializeDockerfileGithubUtil(gitApiUrl(ns), gitApiToken(), ns.getBoolean(DEBUG));

        /* Execute given command. */
        ((ExecutableWithNamespace)runClass.getDeclaredConstructor().newInstance()).execute(ns, dockerfileGitHubUtil);
    }

    static ArgumentParser getArgumentParser() {
        ArgumentParser parser =
                ArgumentParsers.newFor("dockerfile-image-update").addHelp(true).build()
                .description("Image Updates through Pull Request Automator");

        parser.addArgument("-l", "--" + GIT_API_SEARCH_LIMIT)
                .type(Integer.class)
                .setDefault(1000)
                .help("limit the search results for github api (default: 1000)");
        parser.addArgument("-o", "--" + GIT_ORG)
                .help("search within specific organization (default: all of github)");
        /* Currently, because of argument passing reasons, you can only specify one branch. */
        parser.addArgument("-b", "--" + GIT_BRANCH)
                .help("make pull requests for given branch name (default: main)");
        parser.addArgument("-g", "--" + GIT_API)
                .help("link to github api; overrides environment variable");
        parser.addArgument("-f", "--auto-merge").action(Arguments.storeTrue())
                .help("NOT IMPLEMENTED / set to automatically merge pull requests if available");
        parser.addArgument("-m")
                .help("message to provide for pull requests");
        parser.addArgument("-c")
                .help("additional commit message for the commits in pull requests");
        parser.addArgument("-e", "--" + GIT_REPO_EXCLUDES)
                .help("regex of repository names to exclude from pull request generation");
        parser.addArgument("-B")
                .help("additional body text to include in pull requests");
        parser.addArgument("-s", "--" + SKIP_PR_CREATION)
                .type(Boolean.class)
                .setDefault(false) //To prevent null from being returned by the argument
                .help("Only update image tag store. Skip creating PRs");
        parser.addArgument("-x")
                .help("comment snippet mentioned in line just before 'FROM' instruction(Dockerfile)" +
                        "or 'image' instruction(docker-compose) for ignoring a child image. " +
                        "Defaults to 'no-dfiu'");
        parser.addArgument("-t", "--" + FILE_NAMES_TO_SEARCH)
                .type(String.class)
                .setDefault("Dockerfile,docker-compose")
                .help("Comma seperated list of filenames to search & update for PR creation" +
                        "(default: Dockerfile,docker-compose)");
        parser.addArgument("-r", "--" + RATE_LIMIT_PR_CREATION)
                .type(String.class)
                .required(false)
                .help("Use RateLimiting when sending PRs. RateLimiting is enabled only if this value is set it's disabled by default.");
        parser.addArgument("-d", "--" + DEBUG)
                .type(Boolean.class)
                .setDefault(false) //To prevent null from being returned by the argument
                .required(false)
                .help("Enable debug logging, including git wire logs.");
        return parser;
    }

    /*  Adding subcommands to the subcommands list.
            argparse4j allows commands to be truncated, so users can type the first letter (a,c,p) for commands */
    public static Set<ClassPath.ClassInfo> findSubcommands(ArgumentParser parser) throws IOException {
        Subparsers subparsers = parser.addSubparsers()
                .dest(COMMAND)
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

    public static String gitApiToken() {
      return gitApiToken(System::getenv, System::exit);
    }

    public static String gitApiToken(final Function<String, String> envFunc, final Consumer<Integer> exitFunc) {
      final String token = envFunc.apply("git_api_token");
      if (Objects.isNull(token)) {
          log.error("Please provide GitHub token in environment variables.");
          exitFunc.accept(3);
      }
      return token;
    }

    public static String gitApiUrl(final Namespace ns) throws IOException {
      return gitApiUrl(ns, System::getenv);
    }

    public static String gitApiUrl(final Namespace ns, final Function<String, String> envFunc) throws IOException {
      return Optional.ofNullable(
                 Optional.ofNullable(ns.getString(GIT_API))
                   .orElse(envFunc.apply("git_api_url"))
             )
             .orElseThrow(() -> new IOException("No Git API URL in environment variables nor on the commmand line."));
    }

    public static DockerfileGitHubUtil initializeDockerfileGithubUtil(
        final String gitApiUrl,
        final String token,
        final boolean debug) throws IOException
    {
      return initializeDockerfileGithubUtil(gitApiUrl, token, () -> new GitHubBuilder(), debug);
    }

    /* Validate API URL and connect to the API using credentials. */
    public static DockerfileGitHubUtil initializeDockerfileGithubUtil(
        final String gitApiUrl,
        final String token,
        final Supplier<GitHubBuilder> builderFunc,
        final boolean debug) throws IOException {

        GitHub github = shouldAddWireLogger(builderFunc.get(), debug)
                .withEndpoint(gitApiUrl)
                .withOAuthToken(token)
                .build();
        github.checkApiUrlValidity();

        return new DockerfileGitHubUtil(new GitHubUtil(github));
    }

    public static GitHubBuilder shouldAddWireLogger(final GitHubBuilder builder, final boolean debug) {
      if (debug) {
        HttpLoggingInterceptor logger = new HttpLoggingInterceptor();
        logger.setLevel(HttpLoggingInterceptor.Level.HEADERS);
        logger.redactHeader("Authorization");

        builder.withConnector(new OkHttpGitHubConnector(new OkHttpClient.Builder() 
            .addInterceptor(logger)
            .build()));
      }
      return builder;
    }
}
