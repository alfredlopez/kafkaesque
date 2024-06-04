export DOCKER_BUILD_TMP_DIR=$TEMP/docker_tmp
export DOCKER_FLAGS="--tlsverify=false"
#export DOCKER_FLAGS="--tlsverify=false -H tcp://127.0.0.1:2376"

#These exports are SPECIFIC TO YOUR ENVIRONMENT. You need to create a default
#Docker machine and get it's ENVIRONMENT by running docker-machine evn default
#export DOCKER_HOST=tcp://127.0.0.1:2376
#export DOCKER_MACHINE_NAME=default
#export DOCKER_TLS_VERIFY=no
#export DOCKER_CERT_PATH=$USERPROFILE/.docker/machine/machines/default

#export DOCKER_TLS_VERIFY="1"
#export DOCKER_HOST="tcp://192.168.99.100:2376"
export DOCKER_CERT_PATH="/Work/Docker/machines/jumbo"
export DOCKER_MACHINE_NAME="jumbo"
