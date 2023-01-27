#!/bin/bash
set -xeu
# Usage : sh run.sh <gcp project> <subscription name> <gcs bucket name> 

if [ "$#" -ne 3 ] && [ "$#" -ne 4 ]
  then
    echo "Usage : sh run.sh <gcp project> <subscription name> <gcs bucket name> <optional params>" 
    exit -1
fi

SAN_SUBS=`echo "$2" | tr _ -`
JOBNAME=ps2bq-${SAN_SUBS}-${USER}
PROJECT_ID=$1
BUCKET="gs://${3}"

REGION="us-central1"
PIPELINE_NAME=StreamingSourceToBigQuery
TOPIC=`echo $2 | awk -F "-sub" '{print $1}'`
SUBSCRIPTION=$2

# The table need to be precreated with right schema.
BQ_TABLE_NAME=`echo "$2" | tr - _`
BQ_DATASET_ID=`echo "${TOPIC}" | tr - _`
BQ_PROJECT_ID=${PROJECT_ID}

STAGING_BUCKET=${BUCKET}
STAGING_PATH=${STAGING_BUCKET}"/.staging/"
OUTPUT_TABLE=${BQ_PROJECT_ID}:${BQ_DATASET_ID}.prefix_${BQ_TABLE_NAME}

echo bucket=${BUCKET}
echo bqinfo=${OUTPUT_TABLE}
echo jobname=${JOBNAME}

LAUNCH_PARAMS=" \
 --jobName=${JOBNAME} \
 --project=${PROJECT_ID} \
 --runner=DataflowRunner \
 --streaming \
 --region=${REGION} \
 --stagingLocation=${STAGING_PATH} \
 --tempLocation=${BUCKET}/.temp \
 --gcpTempLocation=${BUCKET}/.temp \
 --subscription=projects/${PROJECT_ID}/subscriptions/${SUBSCRIPTION} \
 --outputTable=${OUTPUT_TABLE} \
 --numWorkers=1 \
 --maxNumWorkers=400 \
 --experiments=min_num_workers=1 \
 --workerMachineType=n2d-standard-4 \
 --createBQTable \
 --autoscalingAlgorithm=THROUGHPUT_BASED \
 --experiments=num_pubsub_keys=2048 \
 --experiments=use_pubsub_streaming \
 --enableStreamingEngine \
 --experiments=streaming_engine_job_setting=asymmetric_for_large_loadtest \
 --usePublicIps=false "

if (( $# == 4 ))
then
  LAUNCH_PARAMS=$LAUNCH_PARAMS$4
fi

if [[ -z "${BEAM_VERSION}" ]]; then
  MODIFY_BEAM_VERSION=""
else
  MODIFY_BEAM_VERSION="-Dbeam.version=${BEAM_VERSION}"
fi

mvn compile exec:java -Dexec.mainClass=com.google.cloud.pso.beam.pipelines.${PIPELINE_NAME} -Dexec.cleanupDaemonThreads=false $MODIFY_BEAM_VERSION -Dexec.args="${LAUNCH_PARAMS}"
