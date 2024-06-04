#!/bin/bash

# Make a new docker image
#set -o xtrace
#set -o errexit

source utils/check-params.sh
source utils/docker-vars.sh

printf "Checking docker-specific variables (defined in scripts/utils/docker-vars.sh)"
params=( \
  DOCKER_MACHINE_NAME \
  DOCKER_HOST \
  DOCKER_TLS_VERIFY \
  DOCKER_CERT_PATH \
  DOCKER_BUILD_TMP_DIR \
  DOCKER_FLAGS
)

check-params "${params[@]}";

mkdir -p "$DOCKER_BUILD_TMP_DIR"
pushd $DOCKER_BUILD_TMP_DIR
pwd

rm -rf ./*
cp $PROJECT_PATH/Jumbotron/ci/Dockerfile .
cp -R $PROJECT_PATH/Jumbotron/ci ./Jumbotron

#docker $DOCKER_FLAGS rmi -f jumbotron:latest || true
docker $DOCKER_FLAGS build -t jumbotron:latest .
