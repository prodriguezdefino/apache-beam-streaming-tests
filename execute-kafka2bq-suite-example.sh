#!/bin/bash
set -eu

if [ "$#" -ne 3 ] && [ "$#" -ne 4 ]
  then
    echo "Usage : sh execute-suite-example.sh <gcp project> <a string containing topic/bootstrap-servers> <staging gcs bucket name> <optional params>" 
    exit -1
fi

MORE_PARAMS=""

if (( $# == 4 ))
then
  MORE_PARAMS=$MORE_PARAMS$4
fi

# Beam version var is unset, this will default in the pom.xml definitions
BEAM_VERSION=2.45.0-SNAPSHOT
# Other manual configurations
PROJECT_ID=$1
TOPIC_AND_BOOTSTRAPSERVERS=$2
REGION=us-central1
ZONE=us-central1-a

echo "starting data generator"
pushd streaming-data-generator

JOBNAME=datagen-kafka-`echo "$2" | tr _ -`-${USER}

source ./execute-ps2bq.sh $1 $2 $3 "\
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

JOBNAME=kafka2bq-`echo "$2" | tr _ -`-${USER}

source ./execute-ps2bq.sh $1 $SUBSCRIPTION $3 "\
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