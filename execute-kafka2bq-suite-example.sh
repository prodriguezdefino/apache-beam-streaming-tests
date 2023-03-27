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
RUN_NAME=$2
REGION=us-central1
ZONE=us-central1-a
BUCKET=$2-staging-$1

echo "creating infrastructure"
pushd infra

# we need to create kafka infrastructure, bq dataset and staging bucket 
source ./tf-apply.sh $PROJECT_ID $RUN_NAME false true false false true

# capture the outputs in variables
TF_JSON_OUTPUT=$(terraform output -json)
SUBNET=$(echo $TF_JSON_OUTPUT | jq .subnet.value | tr -d '"')
DF_SA=$(echo $TF_JSON_OUTPUT | jq .df_sa.value | tr -d '"')
KAFKA_IP=$(echo $TF_JSON_OUTPUT | jq .kafka_ip.value | tr -d '"')
REMOTE_JMPSVR_IP=$(echo $TF_JSON_OUTPUT | jq .jmpsrv_ip.value | tr -d '"')

popd

# since the kafka IO implementation needs to be able to read the partition metadata 
# we need to make sure to build the packaged jar files and upload them to the created jump server

sh build.sh

echo "starting data generator"

JOB_NAME=datagen-kafka-`echo "$RUN_NAME" | tr _ -`-${USER}
TOPIC_AND_BOOTSTRAPSERVERS=$RUN_NAME/$KAFKA_IP:9092

# launch the pipeline locally since the sink does not need to validate against the cluster 
java -jar streaming-data-generator/target/streaming-data-generator-bundled-0.0.1-SNAPSHOT.jar \
  --project=$PROJECT_ID \
  --subnetwork=$SUBNET \
  --streaming \
  --stagingLocation=gs://$BUCKET/dataflow/staging \
  --tempLocation=gs://$BUCKET/dataflow/temp \
  --gcpTempLocation=gs://$BUCKET/dataflow/gcptemp \
  --enableStreamingEngine \
  --autoscalingAlgorithm=THROUGHPUT_BASED \
  --numWorkers=50 \
  --maxNumWorkers=1000 \
  --runner=DataflowRunner \
  --workerMachineType=n1-standard-4 \
  --usePublicIps=false \
  --jobName=${JOB_NAME} \
  --region=${REGION} \
  --outputTopic=${TOPIC_AND_BOOTSTRAPSERVERS} \
  --sinkType=KAFKA \
  --className=com.google.cloud.pso.beam.generator.thrift.CompoundEvent \
  --generatorRatePerSec=50000 \
  --sdkHarnessLogLevelOverrides='{\"org.apache.kafka.clients\":\"WARN\"}' \
  --maxRecordsPerBatch=4500 \
  --compressionEnabled=false \
  --serviceAccount=$DF_SA \
  --completeObjects=true $MORE_PARAMS

echo "starting processing pipeline"

# in the case of the ingestion pipeline, the read IO needs to validate and capture the partitions from the cluster
# this can not be achieved from the launcher machine since it may not have connectivity to the cluster in GCP
# we will need to upload the jar file to the jump server created by the infrastructure and then launch the pipeline.
scp -o "StrictHostKeyChecking=no" canonical-streaming-pipelines/target/streaming-pipelines-bundled-0.0.1-SNAPSHOT.jar $USER@$REMOTE_JMPSVR_IP:~

JOB_NAME=kafka2bq-`echo "$RUN_NAME-sub" | tr _ -`-${USER}
BQ_TABLE_NAME=`echo "$RUN_NAME-sub" | tr - _`
BQ_DATASET_ID=`echo "$RUN_NAME" | tr - _`

EXEC_CMD="java -cp ~/streaming-pipelines-bundled-0.0.1-SNAPSHOT.jar com.google.cloud.pso.beam.pipelines.StreamingSourceToBigQuery \
  --project=$PROJECT_ID \
  --subnetwork=$SUBNET \
  --streaming \
  --stagingLocation=gs://$BUCKET/dataflow/staging \
  --tempLocation=gs://$BUCKET/dataflow/temp \
  --gcpTempLocation=gs://$BUCKET/dataflow/gcptemp \
  --enableStreamingEngine \
  --autoscalingAlgorithm=THROUGHPUT_BASED \
  --numWorkers=1 \
  --maxNumWorkers=400 \
  --experiments=min_num_workers=1 \
  --runner=DataflowRunner \
  --workerMachineType=n2d-standard-4 \
  --usePublicIps=false \
  --jobName=${JOB_NAME} \
  --region=${REGION} \
  --serviceAccount=$DF_SA \
  --createBQTable \
  --subscription=${TOPIC_AND_BOOTSTRAPSERVERS} \
  --experiments=use_unified_worker \
  --experiments=use_runner_v2 \
  --sourceType=KAFKA \
  --inputTopic=$RUN_NAME \
  --bootstrapServers=$KAFKA_IP:9092 \
  --consumerGroupId=$RUN_NAME \
  --useStorageApiConnectionPool=false \
  --bigQueryWriteMethod=STORAGE_API_AT_LEAST_ONCE \
  --sdkHarnessLogLevelOverrides='{\"org.apache.kafka.clients\":\"WARN\", \"org.apache.kafka.clients.consumer.internals\":\"WARN\"}' \
  --outputTable=${PROJECT_ID}:${BQ_DATASET_ID}.stream_${BQ_TABLE_NAME} \
  --tableDestinationCount=1 $MORE_PARAMS"

ssh -o "StrictHostKeyChecking=no" $USER@$REMOTE_JMPSVR_IP $EXEC_CMD
