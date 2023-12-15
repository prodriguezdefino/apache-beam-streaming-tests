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
BUCKET=$2-staging-$1

echo "creating infrastructure"
pushd infra

# we need to create a ps topic+sub, bq dataset and staging bucket
source ./tf-apply.sh $PROJECT_ID $TOPIC false true true false false

popd

echo "starting data generator"
pushd streaming-data-generator

JOB_NAME=datagen-ps-`echo "$2" | tr _ -`-${USER}

source ./execute-generator.sh $PROJECT_ID $BUCKET " \
  --jobName=${JOB_NAME} \
  --region=${REGION} \
  --outputTopic=projects/${PROJECT_ID}/topics/${TOPIC} \
  --className=com.google.cloud.pso.beam.generator.thrift.SimplerCompoundEvent \
  --generatorRatePerSec=1000000 "$MORE_PARAMS

popd

echo "starting processing pipeline"
pushd canonical-streaming-pipelines

SUBSCRIPTION=$TOPIC-sub
JOB_NAME=ps2bq-`echo "$SUBSCRIPTION" | tr _ -`-${USER}
BQ_TABLE_NAME=`echo "$SUBSCRIPTION" | tr - _`
BQ_DATASET_ID=`echo "${TOPIC}" | tr - _`
source ./execute-ingestion.sh $PROJECT_ID $BUCKET "\
  --jobName=${JOB_NAME} \
  --region=${REGION} \
  --numWorkers=10 \
  --thriftClassName=com.google.cloud.pso.beam.generator.thrift.SimplerCompoundEvent \
  --subscription=projects/${PROJECT_ID}/subscriptions/${SUBSCRIPTION} \
  --formatToStore=AVRO_GENERIC_RECORD \
  --outputTable=${PROJECT_ID}:${BQ_DATASET_ID}.stream_${BQ_TABLE_NAME} \
  --bigQueryWriteMethod=STORAGE_API_AT_LEAST_ONCE \
  --useStorageApiConnectionPool=true "$MORE_PARAMS

popd
