package com.salesforce.dockerfileimageupdate.itest;

import com.google.common.base.Strings;

public class MainJarFinder {

    public static final String MVN_VERSION = "MVN_VERSION";
    public static final String DEFAULT_VERSION = "1.0-SNAPSHOT";
    public static final String JAR_NAME_FORMAT = "dockerfile-image-update-%s.jar";
    private final Environment environment;

    protected static class Environment {
        public String getVariable(String variable) {
            return System.getenv(variable);
        }
    }

    public MainJarFinder() {
        this.environment = new Environment();
    }

    protected MainJarFinder(Environment environment) {
        this.environment = environment;
    }

    public static String getName() {
        return new MainJarFinder().getJarNameFromEnvironment();
    }

    public String getJarNameFromEnvironment() {
        String version = this.getVersionFromEnvironment();
        return String.format(JAR_NAME_FORMAT, version);
    }

    private String getVersionFromEnvironment() {
        String envVersion = this.environment.getVariable(MVN_VERSION);
        return Strings.isNullOrEmpty(envVersion) ? DEFAULT_VERSION : envVersion;
    }
}
