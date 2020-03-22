FROM maven:3.6-jdk-11 AS build

COPY . .
RUN mvn --version
RUN mvn --quiet --batch-mode install

FROM openjdk:11-jre-slim

LABEL version="0.1"

MAINTAINER dvastrata@salesforce.com

COPY --from=build /dockerfile-image-update/target/dockerfile-image-update-1.0-SNAPSHOT.jar /dockerfile-image-update.jar

ENTRYPOINT ["java", "-jar", "/dockerfile-image-update.jar"]
