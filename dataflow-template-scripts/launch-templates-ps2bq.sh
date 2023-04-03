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

echo "creating infrastructure"
pushd infra

# we need to create a ps topic+sub, bq dataset, bt instance + table and staging bucket 
source ./tf-apply.sh $GCP_PROJECT $RUN_NAME false true true false

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

echo "launching data ingestion pipeline"

SUBSCRIPTION=$TOPIC-sub
JOB_NAME=ps2bq-`echo "$SUBSCRIPTION" | tr _ -`-${USER}
BQ_TABLE_NAME=`echo "$SUBSCRIPTION" | tr - _`
BQ_DATASET_ID=`echo "${TOPIC}" | tr - _`

gcloud dataflow flex-template run $JOB_NAME \
    --template-file-gcs-location $TEMPLATE_PATH/streaming-ingestion-template.json \
    --project $GCP_PROJECT \
    --region $REGION \
    --parameters sourceType=PUBSUB \
    --parameters subscription=projects/${GCP_PROJECT}/subscriptions/${SUBSCRIPTION} \
    --parameters useStorageApiConnectionPool=false \
    --parameters bigQueryWriteMethod=STORAGE_WRITE_API \
    --parameters storageWriteApiTriggeringFrequencySec=5 \
    --parameters numStorageWriteApiStreams=50 \
    --parameters outputTable=${GCP_PROJECT}:${BQ_DATASET_ID}.stream_${BQ_TABLE_NAME} \
    --parameters tableDestinationCount=1 \
    --parameters enableStreamingEngine=true \
    --parameters experiments=use_pubsub_streaming  
