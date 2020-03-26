#!/usr/bin/env bash
set -ex
# Set up Maven settings and release
mkdir -p "${HOME}/.m2"
cp .ci.settings.xml "${HOME}"/.m2/settings.xml
docker run --rm -v "${PWD}":/usr/src/build \
                -v "${HOME}/.m2":/root/.m2 \
                -w /usr/src/build \
                -e encrypted_96e73e3cb232_key \
                -e encrypted_96e73e3cb232_iv \
                -e encrypted_00fae8efff8c_key \
                -e encrypted_00fae8efff8c_iv \
                -e CI_DEPLOY_USER \
                -e CI_DEPLOY_PASSWORD \
                -e GPG_KEY_NAME \
                -e GPG_PASSPHRASE \
                maven:3.6-jdk-11 \
                /bin/bash -c "source .ci.prepare-ssh-gpg.sh && mvn releaser:release"

#Package what we've released to Maven Central
docker build -t salesforce/dockerfile-image-update .

# Push latest docker image
set +x
echo "${DOCKER_PASSWORD}" | docker login -u "${DOCKER_USERNAME}" --password-stdin
set -x
docker push salesforce/dockerfile-image-update

# Tag / push image locked to Maven version of the command-line module
MVN_VERSION=$(cat ./dockerfile-image-update/target/classes/version.txt)
docker tag "salesforce/dockerfile-image-update salesforce/dockerfile-image-update:${MVN_VERSION}"
docker push "salesforce/dockerfile-image-update:${MVN_VERSION}"

# Tag / push image locked to git short hash
SHORT_HASH=$(git rev-parse --short HEAD)
docker tag "salesforce/dockerfile-image-update salesforce/dockerfile-image-update:${SHORT_HASH}"
docker push "salesforce/dockerfile-image-update:${SHORT_HASH}"
