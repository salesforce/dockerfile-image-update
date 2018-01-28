#!/usr/bin/env bash
set -ex
openssl aes-256-cbc -K $encrypted_00fae8efff8c_key -iv $encrypted_00fae8efff8c_iv -in codesigning.asc.enc -out codesigning.asc -d
gpg --fast-import codesigning.asc
cp .travis.settings.xml ${HOME}/.m2/settings.xml
mvn releaser:release
docker login -u="${DOCKER_USER}" -p="${DOCKER_PASSWORD}"
docker push salesforce/dockerfile-image-update
