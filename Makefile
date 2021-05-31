all: mvn-docker-build get-itest-jar-from-maven-image integration-test

# JDK_VERSION should be the JDK version we use to source our container dependencies
JDK_VERSION=11
MVN_SNAPSHOT_VERSION=1.0-SNAPSHOT

DFIU_DIR=dockerfile-image-update
DFIU_TARGET=${DFIU_DIR}/target
DFIU_FULLPATH=${DFIU_TARGET}/dockerfile-image-update-1.0-SNAPSHOT.jar
DFIU_ITEST_DIR=dockerfile-image-update-itest
DFIU_ITEST_TARGET=${DFIU_ITEST_DIR}/target
DFIU_ITEST_FULLPATH=${DFIU_ITEST_TARGET}/dockerfile-image-update-itest-1.0-SNAPSHOT.jar

mvn-docker-build:
	docker build --tag local-maven-build --file Dockerfile.maven --build-arg JDK_VERSION=${JDK_VERSION} .
	mkdir -p ${DFIU_TARGET}
	docker run --rm -v $(CURDIR):/tmp/project local-maven-build /bin/bash -c "cp ${DFIU_FULLPATH} /tmp/project/${DFIU_FULLPATH}"
	docker build --tag salesforce/dockerfile-image-update --build-arg JDK_VERSION=${JDK_VERSION} --build-arg MVN_VERSION=${MVN_SNAPSHOT_VERSION} .

#TODO: Modify docker-compose.yml for local image
#TODO: add --abort-on-container-exit to docker-compose once itests can be made not to flap see issue #21
integration-test:
	@-echo git_api_token=${ITEST_GH_TOKEN} > $(CURDIR)/itest.env
	user_itest_secrets_file_secret=$(CURDIR)/itest.env docker-compose up
	rm itest.env

get-main-project-dirs:
	docker run --rm -v $(CURDIR):/tmp/project local-maven-build /bin/bash -c "cp -R ${DFIU_DIR}/. /tmp/project/${DFIU_DIR}/"
	docker run --rm -v $(CURDIR):/tmp/project local-maven-build /bin/bash -c "cp -R ${DFIU_ITEST_DIR}/. /tmp/project/${DFIU_ITEST_DIR}/"

get-itest-jar-from-maven-image:
	mkdir -p ${DFIU_ITEST_TARGET}
	docker run --rm -v $(CURDIR):/tmp/project local-maven-build /bin/bash -c "cp ${DFIU_ITEST_FULLPATH} /tmp/project/${DFIU_ITEST_FULLPATH}"

get-new-version-from-tag:
	# Get the latest patch revision
	export LATEST_PATCH_VERSION=$$(git describe --match "dockerfile-image-update-1.0.*" --abbrev=0 --tags | sed s/dockerfile-image-update-1.0.// $<); \
	NEW_PATCH_VERSION=1.0.$$(($${LATEST_PATCH_VERSION} + 1)); \
	echo "New patch version: $${NEW_PATCH_VERSION}"; \
	echo $${NEW_PATCH_VERSION} > new_patch_version.txt;

deploy:
	JDK_VERSION=${JDK_VERSION} ./.ci.deploy.sh
