#!/usr/bin/env bash
declare -a vars=(JDK_VERSION encrypted_00fae8efff8c_key encrypted_00fae8efff8c_iv \
    encrypted_96e73e3cb232_key encrypted_96e73e3cb232_iv CI_DEPLOY_USER CI_DEPLOY_PASSWORD \
    GPG_KEY_NAME GPG_PASSPHRASE DOCKER_USERNAME DOCKER_PASSWORD)

for var_name in "${vars[@]}"
do
  if [ -z "$(eval "echo \$$var_name")" ]; then
    echo "Missing environment variable $var_name"
    exit 1
  fi
done

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
                maven:3.6-jdk-"${JDK_VERSION}" \
                /bin/bash -c "source .ci.prepare-ssh-gpg.sh && mvn --quiet --batch-mode releaser:release"

# Get MVN_VERSION
MVN_VERSION=$(cat ./dockerfile-image-update/target/classes/version.txt)
echo "Bundling Maven version ${MVN_VERSION}"

#Package what we've released to Maven Central
docker build --tag salesforce/dockerfile-image-update \
 --build-arg JDK_VERSION="${JDK_VERSION}" --build-arg MVN_VERSION="${MVN_VERSION}" .

# Push latest docker image
set +x
echo "${DOCKER_PASSWORD}" | docker login -u "${DOCKER_USERNAME}" --password-stdin
set -x
docker push salesforce/dockerfile-image-update

# Tag / push image locked to Maven version of the command-line module
docker tag salesforce/dockerfile-image-update salesforce/dockerfile-image-update:"${MVN_VERSION}"
docker push salesforce/dockerfile-image-update:"${MVN_VERSION}"

# Tag / push image locked to git short hash
SHORT_HASH=$(git rev-parse --short HEAD)
docker tag salesforce/dockerfile-image-update salesforce/dockerfile-image-update:"${SHORT_HASH}"
docker push salesforce/dockerfile-image-update:"${SHORT_HASH}"
