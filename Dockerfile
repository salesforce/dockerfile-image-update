FROM openjdk:11-jre-slim

LABEL version="0.1"

MAINTAINER dvastrata@salesforce.com

COPY /dockerfile-image-update/target/dockerfile-image-update-1.0-SNAPSHOT.jar /dockerfile-image-update.jar

ENTRYPOINT ["java", "-jar", "/dockerfile-image-update.jar"]
