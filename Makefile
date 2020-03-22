all: mvn-build integration-test

DFIU_TARGET=dockerfile-image-update/target
DFIU_FULLPATH=${DFIU_TARGET}/dockerfile-image-update-1.0-SNAPSHOT.jar
DFIU_ITEST_TARGET=dockerfile-image-update-itest/target
DFIU_ITEST_FULLPATH=${DFIU_ITEST_TARGET}/dockerfile-image-update-itest-1.0-SNAPSHOT.jar
user_itest_secrets_file_secret=$(CURDIR)/itest.env

mvn-build:
	docker build -t local-maven-build -f Dockerfile.maven .
	mkdir -p ${DFIU_TARGET}
	docker run --rm -v $(CURDIR):/tmp/project local-maven-build /bin/bash -c "cp ${DFIU_FULLPATH} /tmp/project/${DFIU_FULLPATH}"
	docker build -t salesforce/dockerfile-image-update .

#TODO: Modify docker-compose.yml for local image
#TODO: add --abort-on-container-exit to docker-compose once itests can be made not to flap see issue #21
integration-test:
	mkdir -p ${DFIU_ITEST_TARGET}
	docker run --rm -v $(CURDIR):/tmp/project local-maven-build /bin/bash -c "cp ${DFIU_ITEST_FULLPATH} /tmp/project/${DFIU_ITEST_FULLPATH}"
	@-echo git_api_token=${ITEST_GH_TOKEN} > $(CURDIR)/itest.env
	docker-compose up
	rm itest.env
