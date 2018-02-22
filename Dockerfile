FROM openjdk:8

LABEL version="0.1"

MAINTAINER dvastrata@salesforce.com

ADD dockerfile-image-update/target/dockerfile-image-update-1.0-SNAPSHOT.jar /dockerfile-image-update.jar

ENTRYPOINT ["java", "-jar", "/dockerfile-image-update.jar"]
