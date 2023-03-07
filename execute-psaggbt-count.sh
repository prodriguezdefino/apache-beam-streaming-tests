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
BEAM_VERSION=2.46.0-SNAPSHOT
# Other manual configurations
PROJECT_ID=$1
TOPIC=$2
REGION=us-central1
BUCKET=$3

echo "creating infrastructure"
pushd infra

# we need to create a ps topic+sub, bq dataset, bt instance + table and staging bucket 
source ./tf-apply.sh $PROJECT_ID $TOPIC $BUCKET true true true

popd

echo "starting data generator"
pushd streaming-data-generator

JOB_NAME=datagen-ps-`echo "$2" | tr _ -`-${USER}

source ./execute-generator.sh $1 $2 $3 " \
  --jobName=${JOB_NAME} \
  --region=${REGION} \
  --outputTopic=projects/${PROJECT_ID}/topics/${TOPIC} \
  --className=com.google.cloud.pso.beam.generator.thrift.CompoundEvent \
  --generatorRatePerSec=10000 \
  --maxRecordsPerBatch=4500 \
  --compressionEnabled=false \
  --fieldsWithSkew=uuid \
  --skewDegree=10 \
  --skewBuckets=1000 \
  --completeObjects=true "$MORE_PARAMS

popd

echo "starting processing pipeline"
pushd canonical-streaming-pipelines

SUBSCRIPTION=$2-sub
JOB_NAME=ps2bq-`echo "$SUBSCRIPTION" | tr _ -`-${USER}

AGGREGATION_CONFIG_LOCATION="gs://${BUCKET}/aggregation-config.yaml"

# This configuration creates an aggregation which will count the events entering on a 15min window, 
# allowing arrival of up to 1min for late data and it will produce early firings at least every minute (or more if event count is achieved).
# It expects incoming data in Thrift format, with the specified Thrift type as schema. 
# It will group data by the value of the "uuid" field contained in the input type.
echo "aggregations: 
  - type: COUNT 
    window: 
      length: 15m 
      lateness: 1m 
      earlyFirings: 
        enabled: true 
        count: 100000 
        time: 60s 
        accumulating: true 
    input:
      format: THRIFT
      thriftClassName: com.google.cloud.pso.beam.generator.thrift.CompoundEvent
    fields:
      key: 
        - uuid
" | gsutil cp  - ${AGGREGATION_CONFIG_LOCATION}

source ./execute-agg.sh $1 $SUBSCRIPTION $3 "\
  --jobName=${JOB_NAME} \
  --region=${REGION} \
  --subscription=projects/${PROJECT_ID}/subscriptions/${SUBSCRIPTION} \
  --experiments=num_pubsub_keys=2048 \
  --experiments=use_pubsub_streaming \
  --BTProjectId=${PROJECT_ID} \
  --BTInstanceId=aggregations-instance \
  --BTTableId=aggregations \
  --aggregationConfigurationLocation=${AGGREGATION_CONFIG_LOCATION} \
  --outputTable=${PROJECT_ID}.${TOPIC}.aggregation \
 "$MORE_PARAMS

popd
