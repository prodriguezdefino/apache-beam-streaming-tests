#!/bin/bash
set -xeu

if [ "$#" -ne 3 ] && [ "$#" -ne 4 ]
  then
    echo "Usage : sh create-template.sh <gcp project> <run name> <template gcs bucket name> <optional parameters>" 
    exit -1
fi

GCP_PROJECT=$1
RUN_NAME=$2
BUCKET=$3
TOPIC=$RUN_NAME
SUBSCRIPTION=$RUN_NAME-sub
REGION=us-central1
TEMPLATE_PATH=gs://$BUCKET/template
AGGREGATION_CONFIG_LOCATION=gs://$BUCKET/aggregations/aggregation-configuration.yaml

echo "creating infrastructure"
pushd infra

# we need to create a ps topic+sub, bq dataset, bt instance + table and staging bucket 
source ./tf-apply.sh $GCP_PROJECT $RUN_NAME true true true false

popd 

echo "launching data generator"

JOB_NAME=datagen-ps-`echo "$RUN_NAME" | tr _ -`-${USER}

gcloud dataflow flex-template run $JOB_NAME \
    --template-file-gcs-location $TEMPLATE_PATH/streaming-datagenerator-template.json \
    --project $GCP_PROJECT \
    --region $REGION \
    --parameters sinkType=PUBSUB \
    --parameters outputTopic=projects/${GCP_PROJECT}/topics/${TOPIC} \
    --parameters className=com.google.cloud.pso.beam.generator.thrift.CompoundEvent \
    --parameters generatorRatePerSec=10000 \
    --parameters maxRecordsPerBatch=4500 \
    --parameters compressionEnabled=true \
    --parameters completeObjects=true \
    --parameters enableStreamingEngine=true 

echo "launching aggregation pipeline"

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

JOB_NAME=psaggsbt-`echo "$SUBSCRIPTION" | tr _ -`-${USER}

gcloud dataflow flex-template run $JOB_NAME \
    --template-file-gcs-location $TEMPLATE_PATH/streaming-aggregation-template.json \
    --project $GCP_PROJECT \
    --region $REGION \
    --parameters sourceType=PUBSUB \
    --parameters subscription=projects/${GCP_PROJECT}/subscriptions/${SUBSCRIPTION} \
    --parameters aggregationDestination="${GCP_PROJECT}.${RUN_NAME}-instance.${RUN_NAME}" \
    --parameters aggregationConfigurationLocation=${AGGREGATION_CONFIG_LOCATION} \
    --parameters outputTable=${GCP_PROJECT}.${RUN_NAME}.${RUN_NAME}-errors \
    --parameters streaming=true \
    --parameters enableStreamingEngine=true \
    --parameters experiments=use_pubsub_streaming  
