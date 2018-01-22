#!/usr/bin/env bash
cp .travis.settings.xml ${HOME}/.m2/settings.xml
mvn releaser:release
docker login -u="${DOCKER_USER}" -p="${DOCKER_PASSWORD}"
docker push salesforce/dockerfile-image-update
