#!/bin/bash
set -eu

if [ "$#" -ne 2 ] && [ "$#" -ne 3 ]
  then
    echo "Usage : sh execute-suite-example.sh <gcp project> <run name> <optional params>" 
    exit -1
fi

MORE_PARAMS=""

if (( $# == 3 ))
then
  MORE_PARAMS=$MORE_PARAMS$3
fi

# Beam version var is unset, this will default in the pom.xml definitions
BEAM_VERSION=2.46.0-SNAPSHOT
# Other manual configurations
PROJECT_ID=$1
RUN_NAME=$2
REGION=us-central1

GEN_JOB_NAME=datagen-ps-`echo "$RUN_NAME" | tr _ -`-${USER}
SUBSCRIPTION=$RUN_NAME-sub
AGG_JOB_NAME=psaggsbt-`echo "$SUBSCRIPTION" | tr _ -`-${USER}

source stop-suite-example.sh $PROJECT_ID $REGION $RUN_NAME $GEN_JOB_NAME $AGG_JOB_NAME