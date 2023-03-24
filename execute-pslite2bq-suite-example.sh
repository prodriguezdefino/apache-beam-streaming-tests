#!/bin/bash
set -eu

if [ "$#" -ne 2 ] && [ "$#" -ne 3 ]
  then
    echo "Usage : sh execute-suite-example.sh <gcp project> <topic name> <optional params>" 
    exit -1
fi

MORE_PARAMS=""

if (( $# == 3 ))
then
  MORE_PARAMS=$MORE_PARAMS$3
fi

# Beam version var is unset, this will default in the pom.xml definitions
BEAM_VERSION=
# Other manual configurations
PROJECT_ID=$1
TOPIC=$2
REGION=us-central1
ZONE=us-central1-a
BUCKET=$2-staging-$1

echo "creating infrastructure"
pushd infra

# we need to create a psl topic+sub, bq table, bq dataset and staging bucket 
source ./tf-apply.sh $PROJECT_ID $TOPIC false true false true

popd

echo "starting data generator"
pushd streaming-data-generator

JOB_NAME=datagen-pslite-`echo "$2" | tr _ -`-${USER}

source ./execute-generator.sh $PROJECT_ID $BUCKET "\
  --jobName=${JOB_NAME} \
  --region=${REGION} \
  --workerZone=${ZONE} \
  --outputTopic=projects/${PROJECT_ID}/locations/${ZONE}/topics/${TOPIC} \
  --sinkType=PUBSUBLITE \
  --className=com.google.cloud.pso.beam.generator.thrift.CompoundEvent \
  --generatorRatePerSec=10000 \
  --maxRecordsPerBatch=100 \
  --compressionEnabled=false \
  --completeObjects=true "$MORE_PARAMS

popd

echo "starting processing pipeline"
pushd canonical-streaming-pipelines

SUBSCRIPTION=$2-sub
JOB_NAME=pslite2bq-`echo "$SUBSCRIPTION" | tr _ -`-${USER}
BQ_TABLE_NAME=`echo "$SUBSCRIPTION" | tr - _`
BQ_DATASET_ID=`echo "${TOPIC}" | tr - _`

source ./execute-ingestion.sh $1 $SUBSCRIPTION $BUCKET "\
  --jobName=${JOB_NAME} \
  --region=${REGION} \
  --workerZone=${ZONE} \
  --subscription=projects/${PROJECT_ID}/locations/${ZONE}/subscriptions/${SUBSCRIPTION} \
  --sourceType=PUBSUBLITE \
  --useStorageApiConnectionPool=false \
  --outputTable=${PROJECT_ID}:${BQ_DATASET_ID}.stream_${BQ_TABLE_NAME} \
  --bigQueryWriteMethod=STORAGE_API_AT_LEAST_ONCE \
  --tableDestinationCount=1 "$MORE_PARAMS

popd
