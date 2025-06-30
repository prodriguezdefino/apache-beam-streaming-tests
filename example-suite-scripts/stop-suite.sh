#!/bin/bash
set -eu

if [ "$#" -ne 5 ]
  then
    echo "Usage : sh stop-suite-example.sh <gcp project> <region> <run name> <generator job> <ingestion job>"
    exit -1
fi

GCP_PROJECT=$1
REGION=$2
RUN_NAME=$3
GEN_JOB=$4
ING_JOB=$5

function drain_job(){
  JOB_NAME=$1
  REGION=$2
  # get job id
  JOB_ID=$(gcloud dataflow jobs list --filter="name=${JOB_NAME}" --status=active --format="value(JOB_ID)" --region=${REGION} --project=${GCP_PROJECT})
  # drain job
  if [ ! -z "$JOB_ID" ]
  then
    gcloud dataflow jobs drain $JOB_ID --region=${REGION}
    STATUS=""
    while [[ $STATUS != "JOB_STATE_DRAINED" ]];
    do
      echo "draining..."
      sleep 30
      STATUS=$(gcloud dataflow jobs describe ${JOB_ID} --format='value(currentState)' --region=${REGION} --project=${GCP_PROJECT})
    done
  fi
}

echo "draining dataflow jobs..."

drain_job $GEN_JOB $REGION

drain_job $ING_JOB $REGION

echo "removing infrastructure"
pushd infra

# answering anything but `yes` will keep the infra in place for review
source ./tf-destroy.sh $GCP_PROJECT $RUN_NAME false true false false true || 1

popd
