[![codecov](https://codecov.io/gh/salesforce/dockerfile-image-update/branch/main/graph/badge.svg)](https://codecov.io/gh/salesforce/dockerfile-image-update)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.salesforce.dockerfile-image-update/dockerfile-image-update/badge.svg?maxAge=3600)](https://maven-badges.herokuapp.com/maven-central/com.salesforce.dockerfile-image-update/dockerfile-image-update)
[![Docker Image Version (latest semver)](https://img.shields.io/docker/v/salesforce/dockerfile-image-update?label=Docker%20version&sort=semver)](https://hub.docker.com/r/salesforce/dockerfile-image-update/tags)
[![Project Map](https://sourcespy.com/shield.svg)](https://sourcespy.com/github/salesforcedockerfileimageupdate/)

# Dockerfile Image Updater

This tool provides a mechanism to make security updates to docker images at
scale. The tool searches github for declared docker images and sends pull
requests to projects that are not using the desired version of the requested
docker image.

Docker builds images using a declared Dockerfile. Within the Dockerfile,
there is a `FROM` declaration that specifies the base image and a tag that
will be used as the starting layers for the new image. If the base image that
`FROM` depends on is rebuilt, the Docker images that depend on it will never
be updated with the newer layers. This becomes a major problem if the reason
the base image was updated was to fix a security vulnerability. All Docker
images are often based on operating system libraries and these get patched
for security updates quite frequently. This tool, the Dockerfile Image Updater
was created to automatically make sure that child images are updated when the
images they depend on get updated.

## Table of contents

* [User Guide](#user-guide)
  * [What It Does](#what-it-does)
  * [Prerequisites](#prerequisites)
  * [Precautions](#precautions)
  * [How To Use It](#how-to-use-it)
  * [Skip An Image](#additional-notes)
* [Developer Guide](#developer-guide)
  * [Building](#building)
  * [Running locally](#running-locally)
  * [Creating a New Feature](#creating-a-new-feature)
  * [Running Unit Tests](#running-unit-tests)
  * [Running Integration Tests](#running-integration-tests)
  * [Release Process](#release-process)
* [Blogs / Slides](#blogs--slides)

## User Guide

### What it does

The tool has three modes

1. `all` - Reads store that declares the docker images and versions that you
   intend others to use.

   Example:

   ```commandline
   export git_api_url=https://api.github.com
   export git_api_token=my_github_token
   docker run --rm -e git_api_token -e git_api_url \
     salesforce/dockerfile-image-update all image-to-tag-store
   ```

1. `parent` - Searches github for images that use a specified image name and
   sends pull requests if the image tag doesn't match intended tag. The
   intended image with tag is passed in the command line parameters. The
   intended image-to-tag mapping is persisted in a store in a specified git
   repository under the token owner.

   Example:

   ```commandline
   export git_api_url=https://api.github.com
   export git_api_token=my_github_token
   docker run --rm -e git_api_token -e git_api_url \
     salesforce/dockerfile-image-update parent my_org/my_image v1.0.1 \
     image-to-tag-store
   ```

1. `child` - Given a specific git repo, sends a pull request to update the
   image to a given version. You can optionally persist the image version
   combination in the image-to-tag store.

   Example:

   ```commandline
   export git_api_url=https://api.github.com
   export git_api_token=my_github_token
   docker run --rm -e git_api_token -e git_api_url \
     salesforce/dockerfile-image-update child my_gh_org/my_gh_repo \
     my_image_name v1.0.1
   ```

### Prerequisites

In environment variables, please provide:

* `git_api_token` : This is your GitHub token to your account. Set these
  privileges by: going to your GitHub account --> settings -->
  Personal access tokens --> check `repo` and `delete_repo`.
* `git_api_url` : This is the Endpoint URL of the GitHub API. In general
  GitHub, this is `https://api.github.com/`; for Enterprise, this should
  be `https://hostname/api/v3`. (this variable is optional; you can provide it
  through the command line.)

### Precautions

1. This tool may create a LOT of forks in your account. All pull requests
   created are through a fork on your own account.
1. We currently do not operate on forked repositories due to limitations in
   forking a fork on GitHub. We should invest some time in doing this right.
   See [issue #21](https://github.com/salesforce/dockerfile-image-update/issues/22)
1. Submodules are separate repositories and get their own pull requests.

### How to use it

Our recommendation is to run it as a docker container:

```commandline
export git_api_url=https://api.github.com
export git_api_token=my_github_token
docker run --rm -e git_api_token -e git_api_url \
  salesforce/dockerfile-image-update <COMMAND> <PARAMETERS>
```

```commandline
usage: dockerfile-image-update [-h] [-l GHAPISEARCHLIMIT] [-o ORG] [-b BRANCH] [-g GHAPI] [-f] [-m M] [-c C] [-e EXCLUDES] [-B B] [-s {true,false}] [-x X] COMMAND ...

Image Updates through Pull Request Automator

named arguments:
  -h, --help             show this help message and exit
  -l GHAPISEARCHLIMIT, --ghapisearchlimit GHAPISEARCHLIMIT
                         limit the search results for github api (default: 1000)
  -o ORG, --org ORG      search within specific organization (default: all of github)
  -b BRANCH, --branch BRANCH
                         make pull requests for given branch name (default: main)
  -g GHAPI, --ghapi GHAPI
                         link to github api; overrides environment variable
  -f, --auto-merge       NOT IMPLEMENTED / set to automatically merge pull requests if available
  -m M                   message to provide for pull requests
  -c C                   additional commit message for the commits in pull requests
  -e EXCLUDES, --excludes EXCLUDES
                         regex of repository names to exclude from pull request generation
  -B B                   additional body text to include in pull requests
  -s {true,false}, --skipprcreation {true,false}
                         Only update image tag store. Skip creating PRs
  -x X                   comment snippet mentioned in line just before FROM instruction for ignoring a child image. Defaults to 'no-dfiu'
  -r, --rate_limit_pr_creations 
                         Enable rateLimiting for throttling the number of PRs DFIU will cut over a period of time. 
                         The argument value should be in format "<positive_integer>-<ISO-8601_formatted_time>". For example "--rate_limit_pr_creations 60-PT1H" to create 60 PRs per hour.
                         Default is not set, this means no ratelimiting is imposed.
                         
subcommands:
  Specify which feature to perform

  COMMAND                FEATURE
    parent               updates all repositories' Dockerfiles with given base image
    all                  updates all repositories' Dockerfiles
    child                updates one specific repository with given tag
```

#### The `all` command

Specify an image-to-tag store (a repository name on GitHub that contains a
file called store.json); looks through the JSON file and checks/updates all
the base images in GitHub to the tag in the store.

```commandline
usage: dockerfile-image-update all [-h] <IMG_TAG_STORE>

positional arguments:
  <IMG_TAG_STORE>        REQUIRED

optional arguments:
  -h, --help             show this help message and exit
```

#### The `child` command

Forcefully updates a repository's Dockerfile(s) to given tag. If specified a
store, it will also forcefully update the store.

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

Given an image, tag, and store, it will create pull requests for any
Dockerfiles that has the image as a base image and an outdated tag. It also
updates the store.

```commandline
usage: dockerfile-image-update parent [-h] <IMG> <TAG> <IMG_TAG_STORE>

positional arguments:
  <IMG>                  REQUIRED
  <TAG>                  REQUIRED
  <IMG_TAG_STORE>        REQUIRED

optional arguments:
  -h, --help             show this help message and exit
```

### Skip An Image

In case you want the tool to skip updating a particular image tag then add a comment `no-dfiu` after the `FROM` declaration in the Dockerfile. The tool will process the comment following `FROM` declaration and if `no-dfiu` is mentioned, pull request for that image tag will be ignored. You can use an alternate comment string by passing an additional command line parameter `-x IGNORE_IMAGE_STRING`. In that case string mentioned with the parameter, will be used for skipping PR creation.

Example:

```
FROM imagename:imagetag # no-dfiu
```

### PR throttling

In case you want to throttle the number of PRs cut by DFIU over a period of time,
set --rate_limit_pr_creations with appropriate value. 

##### Default case:

By default, this feature is disabled. This will be enabled when argument ``--rate_limit_pr_creations`` will be passed
with appropriate value.

```
example: dockerfile-image-update all image-tag-store-repo-falcon //throttling will be disabled by default
```

##### Configuring the rate limit:

Below are some examples that will throttle the number of PRs cut based on values passed to the
argument ``--rate_limit_pr_creations``
The argument value should be in format ``<positive_integer>-<ISO-8601_formatted_time>``.
For example ``--rate_limit_pr_creations 60-PT1H`` would mean the tool will cut 60 PRs every hour and the rate of adding
a new PR will be (PT1H/60) i.e. one minute.
This will distribute the load uniformly and avoid sudden spikes, The process will go in waiting state until next PR
could be sent.

Below are some more examples:

```
Usage: 
    dockerfile-image-update --rate_limit_pr_creations 60-PT1H all image-tag-store-repo-falcon //DFIU can send up to 60 PRs per hour.
    dockerfile-image-update --rate_limit_pr_creations 500-PT1H all image-tag-store-repo-falcon //DFIU can send up to 500 PRs per hour.
    dockerfile-image-update --rate_limit_pr_creations 86400-PT24H all image-tag-store-repo-falcon //DFIU can send up to 1 PRs per second.
    dockerfile-image-update --rate_limit_pr_creations 1-PT1S all image-tag-store-repo-falcon //Same as above. DFIU can send up to 1 PRs per second.
    dockerfile-image-update --rate_limit_pr_creations 5000 all image-tag-store-repo-falcon //rate limiting will be disabled because argument is not in correct format.
```

## Developer Guide

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

Under [dockerfile-image-update/src/main/java/com/salesforce/dva/dockerfileimageupdate/subcommands/impl](https://github.com/salesforce/dockerfile-image-update/tree/main/dockerfile-image-update/src/main/java/com/salesforce/dockerfileimageupdate/subcommands/impl),
create a new class `YOUR_FEATURE.java`.
Make sure it implements `ExecutableWithNamespace` and has the `SubCommand`
annotation with a `help`, `requiredParams`, and `optionalParams`.
Then, under the `execute` method, code what you want this tool to do.

### Running unit tests

Run unit tests by running `mvn test`.

### Running integration tests

Before you run the integration tests (locally):

1. Make sure that you have access to the github orgs specified in
   [TestCommon.ORGS](https://github.com/salesforce/dockerfile-image-update/blob/main/dockerfile-image-update-itest/src/main/java/com/salesforce/dockerfileimageupdate/itest/tests/TestCommon.java#L33).
   You likely will need to change it to three orgs where you have permissions
   to create repositories.
1. Make sure you have `git_api_url=https://api.github.com` in `/dockerfile-image-update-itest/itest.env`,
   or set it to your internal GitHub Enterprise.
1. Make sure you have a secret file which contains the `git_api_token`.
   The token needs to have `delete_repo, repo` permissions.
   You can generate your token by going to [personal access tokens](https://github.com/settings/tokens/new)
   in GitHub. Once you have your token place it in a file:

   ```
   echo git_api_token=[copy personal access token here] > ${HOME}/.dfiu-itest-token
   ```

1. Export the following environment variable to point to the file:

   ```
   export user_itest_secrets_file_secret=${HOME}/.dfiu-itest-token
   ```

1. Run integration tests by running

   ```
   make integration-test
   ```

### Release Process

We currently use GitHub Actions and Releases. In order to collect dependency
updates from dependabot and any other minor changes, we've switched to a process
to manually trigger the release process. For now, that looks like the following:

#### 1. Versioned Git Tag

* Decide what version you desire to have. If you want to bump the major or minor
  version then you need to bump the `MVN_SNAPSHOT_VERSION` in the [Makefile](https://github.com/salesforce/dockerfile-image-update/blob/main/Makefile#L5)
  and in the
  [Dockerfile](https://github.com/salesforce/dockerfile-image-update/blob/main/Dockerfile#L4)
  before proceeding to the next steps. For example
  `MVN_SNAPSHOT_VERSION=1.0-SNAPSHOT` to
  `MVN_SNAPSHOT_VERSION=2.0-SNAPSHOT`.
* After PRs have been merged to the primary branch, go to the Actions tab
  and trigger the `Release new version` Workflow. This will build,
  integration test, deploy the latest version to Docker Hub and Maven
  Central, and tag that commit hash with the next semantic version.

#### 2. Cut Release with Release Notes

* PRs continually get updated with labels by [Pull Request Labeler](https://github.com/actions/labeler)
  and that helps set us up for nice release notes by [Release Drafter](https://github.com/release-drafter/release-drafter).
* Once that release has been tagged you can go to the draft release which
  is continually updated by [Release Drafter](https://github.com/release-drafter/release-drafter)
  and select the latest tag to associate with that release. Change the
  version to reflect the same version as the tag (`1.0.${NEW_VERSION}`).
  Take a look at the release notes to make sure that PRs are categorized
  correctly. The categorization is based on the labels of the PRs. You can
  either fix the labels on the PRs, which will trigger the
  [release drafter action](https://github.com/salesforce/dockerfile-image-update/actions/workflows/release-drafter.yml),
  or simply modify the release notes before publishing. Ideally we'll automate
  this to run at the end of the triggered workflow with something like
  [svu](https://github.com/caarlos0/svu).

### Checking Code Climate Locally

If you'd like to check [Code Climate](https://codeclimate.com/quality/)
results locally you can run the following:

```
docker run --interactive --tty --rm \
 --env CODECLIMATE_CODE="$(pwd)" \
 --volume "$(pwd)":/code \
 --volume /var/run/docker.sock:/var/run/docker.sock \
 --volume /tmp/cc:/tmp/cc \
 codeclimate/codeclimate analyze README.md
```

# Blogs / Slides

* [2018-03-13 Salesforce Engineering Blog - Open Sourcing Dockerfile Image Update](https://engineering.salesforce.com/open-sourcing-dockerfile-image-update-6400121c1a75)
* [2018 SRECon 18 - Auto-Cascading Security Updates Through Docker Images](https://www.slideshare.net/AndreyFalko1/srecon18americas-lightning-talk-autocascading-security-updates-through-docker-images?qid=c659b92a-aa60-4ef1-942a-de7b4fb66ad2&v=&b=&from_search=1)
* [2018-10-22 - Lyft Engineering Blog -
  The Challenges Behind Rolling Out Security Updates To Your Docker Images](https://eng.lyft.com/the-challenges-behind-rolling-out-security-updates-to-your-docker-images-86106de47ece)
