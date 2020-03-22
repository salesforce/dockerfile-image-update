#!/usr/bin/env bash
set -ex
# Import SSH key to access GitHub for versioning
openssl aes-256-cbc -K $encrypted_96e73e3cb232_key -iv $encrypted_96e73e3cb232_iv \
    -in id_rsa_dockerfile_image_update.enc -out id_rsa_dockerfile_image_update -d
mkdir -p ${HOME}/.ssh
mv -f id_rsa_dockerfile_image_update ${HOME}/.ssh/id_rsa

# Import code signing keys
openssl aes-256-cbc -K $encrypted_00fae8efff8c_key -iv $encrypted_00fae8efff8c_iv -in codesigning.asc.enc -out codesigning.asc -d
gpg --fast-import codesigning.asc

# Remove code signing keys (since the releaser plugin requires a clean git workspace)
shred --remove codesigning.asc

# Set up Maven settings and release
cp .travis.settings.xml ${HOME}/.m2/settings.xml
docker run -d --rm -v "$PWD":/usr/src/build -v "$HOME/.m2":/root/.m2  -v "$HOME/.ssh":/root/.ssh -v "$HOME/.gnupg":/root/.gnupg -v "$PWD/target:/usr/src/mymaven/target" -w /usr/src/build -e CI_DEPLOY_USER -e CI_DEPLOY_PASSWORD -e GPG_KEY_NAME -e GPG_PASSPHRASE maven:3.6-jdk-11 releaser:release

# Push latest docker image
set +x
echo "${DOCKER_PASSWORD}" | docker login -u "${DOCKER_USERNAME}" --password-stdin
set -x
docker push salesforce/dockerfile-image-update

# Tag / push image locked to Maven version of the command-line module
MVN_VERSION=$(cat ./dockerfile-image-update/target/classes/version.txt)
docker tag salesforce/dockerfile-image-update salesforce/dockerfile-image-update:${MVN_VERSION}
docker push salesforce/dockerfile-image-update:${MVN_VERSION}

# Tag / push image locked to git short hash
SHORT_HASH=$(git rev-parse --short HEAD)
docker tag salesforce/dockerfile-image-update salesforce/dockerfile-image-update:${SHORT_HASH}
docker push salesforce/dockerfile-image-update:${SHORT_HASH}
