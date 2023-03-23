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

function drain_job(){
  JOB_NAME=$1
  REGION=$2
  # get job id 
  JOB_ID=$(gcloud dataflow jobs list --filter="name=${JOB_NAME}" --status=active --format="value(JOB_ID)" --region=${REGION})
  # drain job
  if [ ! -z "$JOB_ID" ] 
  then 
    gcloud dataflow jobs drain $JOB_ID --region=${REGION}
    STATUS=""
    while [[ $STATUS != "JOB_STATE_DRAINED" ]]; 
    do
      echo "draining..." 
      sleep 30
      STATUS=$(gcloud dataflow jobs describe ${JOB_ID} --format='value(currentState)' --region=${REGION}) 
    done
  fi
}

echo "draining dataflow jobs..."

GEN_JOB_NAME=datagen-ps-`echo "$2" | tr _ -`-${USER}

drain_job $GEN_JOB_NAME $REGION

SUBSCRIPTION=$RUN_NAME-sub
AGG_JOB_NAME=psaggsbt-`echo "$SUBSCRIPTION" | tr _ -`-${USER}

drain_job $AGG_JOB_NAME $REGION

echo "removing infrastructure"
pushd infra

# answering anything but `yes` will keep the infra in place for review
source ./tf-destroy.sh $PROJECT_ID $RUN_NAME true true true false || 1

popd
