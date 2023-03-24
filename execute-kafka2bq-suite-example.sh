#!/bin/bash
set -eu

if [ "$#" -ne 2 ] && [ "$#" -ne 3 ]
  then
    echo "Usage : sh execute-suite-example.sh <gcp project> <a string containing topic/bootstrap-servers> <optional params>" 
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
TOPIC_AND_BOOTSTRAPSERVERS=$2
REGION=us-central1
ZONE=us-central1-a
BUCKET=$2-staging-$1

echo "starting data generator"
pushd streaming-data-generator

JOB_NAME=datagen-kafka-`echo "$2" | tr _ -`-${USER}

source ./execute-generator.sh $PROJECT_ID $BUCKET "\
  --jobName=${JOB_NAME} \
  --region=${REGION} \
  --outputTopic=${TOPIC_AND_BOOTSTRAPSERVERS} \
  --sinkType=PUBSUBLITE \
  --className=com.google.cloud.pso.beam.generator.thrift.CompoundEvent \
  --generatorRatePerSec=50000 \
  --maxRecordsPerBatch=4500 \
  --compressionEnabled=true \
  --completeObjects=true "$MORE_PARAMS

popd

echo "starting processing pipeline"
pushd canonical-streaming-pipelines

JOB_NAME=kafka2bq-`echo "$2" | tr _ -`-${USER}

source ./execute-ingestion.sh $PROJECT_ID $SUBSCRIPTION $BUCKET "\
  --jobName=${JOB_NAME} \
  --region=${REGION} \
  --subscription=${TOPIC_AND_BOOTSTRAPSERVERS} \
  --experiments=use_unified_worker \
  --experiments=use_runner_v2 \
  --sourceType=KAFKA \
  --useStorageApiConnectionPool=true \
  --bigQueryWriteMethod=STORAGE_API_AT_LEAST_ONCE \
  --tableDestinationCount=1 "$MORE_PARAMS

popd
