ARG JDK_VERSION
FROM openjdk:${JDK_VERSION}-jre-slim

ARG MVN_VERSION
LABEL version=${MVN_VERSION}

MAINTAINER dvastrata@salesforce.com

COPY /dockerfile-image-update/target/dockerfile-image-update-${MVN_VERSION}.jar /dockerfile-image-update-${MVN_VERSION}.jar

ENTRYPOINT ["java", "-jar", "/dockerfile-image-update-${MVN_VERSION}.jar"]
