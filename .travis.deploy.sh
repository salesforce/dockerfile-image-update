#!/usr/bin/env bash
set -ex
# Import code signing keys
openssl aes-256-cbc -K $encrypted_00fae8efff8c_key -iv $encrypted_00fae8efff8c_iv -in codesigning.asc.enc -out codesigning.asc -d
gpg --fast-import codesigning.asc
# Remove code signing keys (since the releaser plugin requires a clean git workspace)
rm codesigning.asc
# Set up Maven settings and release
cp .travis.settings.xml ${HOME}/.m2/settings.xml
mvn releaser:release
# Push latest docker image
docker login -u="${DOCKER_USER}" -p="${DOCKER_PASSWORD}"
docker push salesforce/dockerfile-image-update
