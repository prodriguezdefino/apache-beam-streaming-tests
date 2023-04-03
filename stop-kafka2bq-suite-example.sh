#!/bin/bash
set -eu

if [ "$#" -ne 2 ] && [ "$#" -ne 3 ]
  then
    echo "Usage : sh stop-kafka2bq-suite-example.sh <gcp project> <run name> <optional params>" 
    exit -1
fi

MORE_PARAMS=""

if (( $# == 3 ))
then
  MORE_PARAMS=$MORE_PARAMS$3
fi

# Other manual configurations
PROJECT_ID=$1
RUN_NAME=$2
REGION=us-central1

GEN_JOB_NAME=datagen-kafka-`echo "$2" | tr _ -`-${USER}
ING_JOB_NAME=kafka2bq-`echo "$RUN_NAME-sub" | tr _ -`-${USER}

source stop-suite-example.sh $PROJECT_ID $REGION $RUN_NAME $GEN_JOB_NAME $ING_JOB_NAME