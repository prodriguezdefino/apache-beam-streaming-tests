#!/bin/bash
set -eu

if [ "$#" -ne 3 ] && [ "$#" -ne 4 ]
  then
    echo "Usage : sh execute-suite-example.sh <gcp project> <topic name> <staging gcs bucket name> <optional params>" 
    exit -1
fi

MORE_PARAMS=""

if (( $# == 4 ))
then
  MORE_PARAMS=$MORE_PARAMS$4
fi

# Beam version var is unset, this will default in the pom.xml definitions
BEAM_VERSION=
# Other manual configurations
PROJECT_ID=$1
TOPIC=$2
REGION=us-central1
BUCKET=$3 

echo "creating infrastructure"
pushd infra

# we need to create a ps topic+sub, bt instance + table, bq dataset and staging bucket 
source ./tf-apply.sh $PROJECT_ID $TOPIC $BUCKET false true true

popd

echo "starting data generator"
pushd streaming-data-generator

JOB_NAME=datagen-ps-`echo "$2" | tr _ -`-${USER}

source ./execute-generator.sh $1 $2 $3 " \
  --jobName=${JOB_NAME} \
  --region=${REGION} \
  --outputTopic=projects/${PROJECT_ID}/topics/${TOPIC} \
  --className=com.google.cloud.pso.beam.generator.thrift.CompoundEvent \
  --generatorRatePerSec=200000 \
  --maxRecordsPerBatch=4500 \
  --compressionEnabled=true \
  --completeObjects=true "$MORE_PARAMS

popd

echo "starting processing pipeline"
pushd canonical-streaming-pipelines

SUBSCRIPTION=$2-sub
JOB_NAME=ps2bq-`echo "$SUBSCRIPTION" | tr _ -`-${USER}

source ./execute-ps2bq.sh $1 $SUBSCRIPTION $3 "\
  --jobName=${JOB_NAME} \
  --region=${REGION} \
  --thriftClassName=com.google.cloud.pso.beam.generator.thrift.CompoundEvent \
  --subscription=projects/${PROJECT_ID}/subscriptions/${SUBSCRIPTION} \
  --experiments=num_pubsub_keys=2048 \
  --experiments=use_pubsub_streaming \
  --useStorageApiConnectionPool=false \
  --bigQueryWriteMethod=STORAGE_WRITE_API \
  --storageWriteApiTriggeringFrequencySec=5 \
  --numStorageWriteApiStreams=50 \
  --tableDestinationCount=1 "$MORE_PARAMS

popd
