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

# Other manual configurations
PROJECT_ID=$1
RUN_NAME=$2
REGION=us-central1

GEN_JOB_NAME=datagen-pslite-`echo "$2" | tr _ -`-${USER}
SUBSCRIPTION=$RUN_NAME-sub
ING_JOB_NAME=pslite2bq-`echo "$SUBSCRIPTION" | tr _ -`-${USER}

SCRIPT_DIR="$(dirname "$(readlink -f "$0")")"

source $SCRIPT_DIR/stop-suite.sh $PROJECT_ID $REGION $RUN_NAME $GEN_JOB_NAME $ING_JOB_NAME