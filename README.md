[![Build Status](https://travis-ci.org/salesforce/dockerfile-image-update.svg?branch=master)](https://travis-ci.org/salesforce/dockerfile-image-update)
[![Coverage Status](https://coveralls.io/repos/github/salesforce/dockerfile-image-update/badge.svg?branch=master&service=github)](https://coveralls.io/github/salesforce/dockerfile-image-update?branch=master)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.salesforce.dockerfile-image-update/dockerfile-image-update/badge.svg?maxAge=3600)](https://maven-badges.herokuapp.com/maven-central/com.salesforce.dockerfile-image-update/dockerfile-image-update)
[![Dependabot Status](https://api.dependabot.com/badges/status?host=github&repo=salesforce/dockerfile-image-update)](https://dependabot.com)

# Dockerfile Image Updater

This tool provides a mechanism to make security updates to docker images at scale. The tool searches github for declared docker images and sends pull requests to projects that are not using the desired version of the requested docker image.

Docker builds images using a declared Dockerfile. Within the Dockerfile, there is a `FROM` declaration that specifies the base image and a tag that will be used as the starting layers for the new image. If the base image that `FROM` depends on is rebuilt, the Docker images that depend on it will never be updated with the newer layers. This becomes a major problem if the reason the base image was updated was to fix a security vulnerability. All Docker images are often based on operating system libraries and these get patched for security updates quite frequently. This tool, the Dockerfile Image Updater was created to automatically make sure that child images are updated when the images they depend on get updated.

## Table of contents

 * [User Guide](#user-guide)
    * [What It Does](#what-it-does)
    * [Prerequisites](#prerequisites)
    * [Precautions](#precautions)
    * [How To Use It](#how-to-use-it)
 * [Developer Guide](#developer-guide)
    * [Building](#building)
    * [Running locally](#running-locally)
    * [Creating a New Feature](#creating-a-new-feature)
    * [Running Unit Tests](#running-unit-tests)
    * [Running Integration Tests](#running-integration-tests)
 * [Blogs / Slides](#blogs--slides)
 
User Guide
==========
### What it does
The tool has three modes
 1. `all` - Reads store that declares the docker images and versions that you intend others to use. 
 Example:
```commandline
export git_api_url=https://api.github.com
export git_api_token=my_github_token
docker run --rm -e git_api_token -e git_api_url salesforce/dockerfile-image-update all image-to-tag-store
```
 2. `parent` - Searches github for images that use a specified image name and sends pull requests if the image tag doesn't match intended tag. The intended image with tag is passed in the command line parameters. The intended image-to-tag mapping is persisted in a store in a specified git repository under the token owner. 
Example:
```commandline
export git_api_url=https://api.github.com
export git_api_token=my_github_token
docker run --rm -e git_api_token -e git_api_url salesforce/dockerfile-image-update parent my_org/my_image v1.0.1 image-to-tag-store
```
 3. `child` - Given a specific git repo, sends a pull request to update the image to a given version. You can optionally persist the image version combination in the image-to-tag store. 
Example:
```commandline
export git_api_url=https://api.github.com
export git_api_token=my_github_token
docker run --rm -e git_api_token -e git_api_url salesforce/dockerfile-image-update child my_gh_org/my_gh_repo my_image_name v1.0.1
```

### Prerequisites
In environment variables, please provide:
 * `git_api_token` : This is your GitHub token to your account. Set these privileges by: going to your GitHub account --> settings --> Personal access tokens --> check `repo` and `delete_repo`.
 * `git_api_url` : This is the Endpoint URL of the GitHub API. In general GitHub, this is `https://api.github.com/`; for Enterprise, this should be `https://hostname/api/v3`. (this variable is optional; you can provide it through the command line.)

### Precautions
1. This tool may create a LOT of forks in your account. All pull requests created are through a fork on your own account.
2. We currently do not operate on forked repositories due to limitations in forking a fork on GitHub.
We should invest some time in doing this right. See [issue #21](https://github.com/salesforce/dockerfile-image-update/issues/22) 
3. Submodules are separate repositories and get their own pull requests.

### How to use it
Our recommendation is to run it as a docker container:
```commandline
export git_api_url=https://api.github.com
export git_api_token=my_github_token
docker run --rm -e git_api_token -e git_api_url salesforce/dockerfile-image-update <COMMAND> <PARAMETERS>
```

```commandline
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

#### The `all` command
Specify an image-to-tag store (a repository name on GitHub that contains a file called store.json); looks through the JSON file and checks/updates all the base images in GitHub to the tag in the store.

```commandline
usage: dockerfile-image-update all [-h] <IMG_TAG_STORE>

positional arguments:
  <IMG_TAG_STORE>        REQUIRED

optional arguments:
  -h, --help             show this help message and exit
```

#### The `child` command
Forcefully updates a repository's Dockerfile(s) to given tag. If specified a store, it will also forcefully update the store.

```commandline
usage: dockerfile-image-update child [-h] [-s <IMG_TAG_STORE>] <GIT_REPO> <IMG> <FORCE_TAG>

positional arguments:
  <GIT_REPO>             REQUIRED
  <IMG>                  REQUIRED
  <FORCE_TAG>            REQUIRED

optional arguments:
  -h, --help             show this help message and exit
  -s <IMG_TAG_STORE>     OPTIONAL
```

#### The `parent` command
Given an image, tag, and store, it will create pull requests for any Dockerfiles that has the image as a base image and an outdated tag. It also updates the store. 

```commandline
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
### Building
```commandline
git clone https://github.com/salesforce/dockerfile-image-update.git
cd dockerfile-image-update
mvn clean install
```

### Running locally
```commandline
cd dockerfile-image-update/target
java -jar dockerfile-image-update-1.0-SNAPSHOT.jar <COMMAND> <PARAMETERS>
```

### Creating a new feature
Under [dockerfile-image-update/src/main/java/com/salesforce/dva/dockerfileimageupdate/subcommands/impl](https://github.com/salesforce/dockerfile-image-update/tree/master/dockerfile-image-update/src/main/java/com/salesforce/dockerfileimageupdate/subcommands/impl), 
create a new class `YOUR_FEATURE.java`. Make sure it implements `ExecutableWithNamespace` and has the `SubCommand` 
annotation with a `help`, `requiredParams`, and `optionalParams`. Then, under the `execute` method, code what you want this tool to do.

### Running unit tests
Run unit tests by running `mvn test`. 
 
### Running integration tests
Before you run the integration tests (locally):
 1. Make sure that you have access to the github orgs specified in [TestCommon.ORGS](https://github.com/salesforce/dockerfile-image-update/blob/master/dockerfile-image-update-itest/src/main/java/com/salesforce/dockerfileimageupdate/itest/tests/TestCommon.java#L33). You likely will need to change it to three
    orgs where you have permissions to create repositories. 
 2. Make sure you have `git_api_url=https://api.github.com` in `/dockerfile-image-update-itest/itest.env`, 
    or set it to your internal GitHub Enterprise.
 3. Make sure you have a secret file which contains the `git_api_token`. 
    The token needs to have `delete_repo, repo` permissions. 
    You can generate your token by going to [personal access tokens](https://github.com/settings/tokens/new) in GitHub. 
    Once you have your token place it in a file: 
    ```
    echo git_api_token=[copy personal access token here] > ${HOME}/.dfiu-itest-token
    ```   
 4. Export the following environment variable to point to the file: 
    ```
    export user_itest_secrets_file_secret=${HOME}/.dfiu-itest-token
    ```
 5. Run integration tests by running 
    ```
    make itest-local-changes
    ```

Blogs / Slides
==============

* [2018-03-13 Salesforce Engineering Blog - Open Sourcing Dockerfile Image Update](https://engineering.salesforce.com/open-sourcing-dockerfile-image-update-6400121c1a75)
* [2018 SRECon 18 - Auto-Cascading Security Updates Through Docker Images](https://www.slideshare.net/AndreyFalko1/srecon18americas-lightning-talk-autocascading-security-updates-through-docker-images?qid=c659b92a-aa60-4ef1-942a-de7b4fb66ad2&v=&b=&from_search=1)
* [2018-10-22 - Lyft Engineering Blog - The Challenges Behind Rolling Out Security Updates To Your Docker Images](https://eng.lyft.com/the-challenges-behind-rolling-out-security-updates-to-your-docker-images-86106de47ece)
