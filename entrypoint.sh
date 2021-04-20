#!/usr/bin/env sh
set -x
exec java -jar /dockerfile-image-update-${MVN_VERSION}.jar "$@"
