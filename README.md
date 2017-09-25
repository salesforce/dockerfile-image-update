# Dockerfile Image Updater

Docker builds applications using a file called Dockerfile. Within the Dockerfile, we have a specified base image and a tag. If the base image is rebuilt onto a new tag, the application that relies on this base image will never be updated to the recent tag: this is especially an issue if the update is a security update. This tool, the Dockerfile Image Updater was created to fix that problem.

## Table of contents

 * [User Guide](#user-guide)
    * [What It Does](#what-it-does)
    * [Prerequisites](#prerequisites)
    * [Precautions](#precautions)
    * [How To Use It](#how-to-use-it)
 * [Developer Guide](#developer-guide)
    * [Creating a New Feature](#creating-a-new-feature)
    * [Running Unit Tests](#running-unit-tests)
    * [Running Integration Tests](#running-integration-tests)
 
User Guide
==========
### What it does
The tool takes in the following parameters: image name, tag, a potential store that holds images to tags. It will search GitHub for any repositories that have the specified image as a base image and replace them with the tag. The repositories are **NOT** automatically modified; pull requests are opened in that repository.

### Prerequisites
In environment variables, please provide:
 * `git_api_token` : This is your GitHub token to your account. Set these privileges by: going to your GitHub account --> settings --> Personal access tokens --> check `repo` and `delete_repo`.
 * `git_api_url` : This is the Endpoint URL of the GitHub API. In general GitHub, this is `https://api.github.com/`; for Enterprise, this should be `https://hostname/api/v3`. (this variable is optional; you can provide it through the command line.)

### Precautions
This tool may create a LOT of forks in your account. All pull requests created are through a fork on your own account.

### How to use it
```
git clone https://github.com/salesforce/dockerfile-image-update.git
cd dockerfile-image-update
mvn clean install
cd dockerfile-image-update-cmdtool/target
java -jar dockerfile-image-update.jar <COMMAND> <PARAMETERS>
```

    ```
    usage: dockerfile-image-update [-h] [-o ORG] [-b BRANCH] [-g GHAPI] [-f] [-m M] [-c C] COMMAND ...
    
    Image Updates through Pull Request Automator
    
    optional arguments:
      -h, --help                   show this help message and exit
      -o ORG, --org ORG            search within specific organization (default: all of github)
      -b BRANCH, --branch BRANCH   make pull requests for given branch name (default: master)
      -g GHAPI, --ghapi GHAPI      link to github api; overrides environment variable
      -f, --auto-merge             NOT IMPLEMENTED / set to automatically merge pull requests if available
      -m PULL_REQ_MESSAGE          message to provide for pull requests
      -c COMMIT_MESSAGE            additional commit message for the commits in pull requests
    
    subcommands:
      Specify which feature to perform
    
      COMMAND                FEATURE
        all                  updates all repositories' Dockerfiles
        child                updates one specific repository with given tag
        parent               updates all repositories' Dockerfiles with given base image
    ```
* The `all` command: specify an image-to-tag store (a repository name on GitHub that contains a file called store.json); looks through the JSON file and checks/updates all the base images in GitHub to the tag in the store.
    ```
    usage: dockerfile-image-update all [-h] <IMG_TAG_STORE>
    
    positional arguments:
      <IMG_TAG_STORE>        REQUIRED
    
    optional arguments:
      -h, --help             show this help message and exit
    ```
* The `child` command: forcefully updates a repository's Dockerfile(s) to given tag. If specified a store, it will also forcefully update the store.
    ```
    usage: dockerfile-image-update child [-h] [-s <IMG_TAG_STORE>] <GIT_REPO> <IMG> <FORCE_TAG>
    
    positional arguments:
      <GIT_REPO>             REQUIRED
      <IMG>                  REQUIRED
      <FORCE_TAG>            REQUIRED
    
    optional arguments:
      -h, --help             show this help message and exit
      -s <IMG_TAG_STORE>     OPTIONAL
    ```
* The `parent` command: given an image, tag, and store, it will create pull requests for any Dockerfiles that has the image as a base image and an outdated tag. also updates the store. 
    ```
    usage: dockerfile-image-update parent [-h] <IMG> <TAG> <IMG_TAG_STORE>
    
    positional arguments:
      <IMG>                  REQUIRED
      <TAG>                  REQUIRED
      <IMG_TAG_STORE>        REQUIRED
    
    optional arguments:
      -h, --help             show this help message and exit
    ```

Developer Guide
===============
### Creating a new feature
Under dockerfile-image-update-cmdtool/src/main/java/com/salesforce/dva/dockerfileimageupdate/subcommands/impl, create a new class `YOUR_FEATURE.java`. Make sure it implements `ExecutableWithNamespace` and has the `SubCommand` annotation with a `help`, `requiredParams`, and `optionalParams`. Then, under the `execute` method, code what you want this tool to do.

### Running unit tests
Run unit tests by running `mvn test`. 
 
### Running integration tests
Before you run the integration tests (locally):
 * Make sure you have `git_api_url` in `/dockerfile-image-update-itest/itest.env`.
 * Make sure you have a secret file which contains the `git_api_token`
 * Run this `export user_itest_secrets_file_secret=/path/to/secretFile` in you shell
 * The user of the token provided should be part of three organizations: `dva-tests`, `dva-tests-2`, `dva-tests-3`.
 * Run integration tests by running `make itest-local-changes`.

