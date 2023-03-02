#!/bin/bash
set -eu
# Usage : sh run.sh <gcp project> <subscription name> <gcs bucket name> 

if [ "$#" -ne 3 ] && [ "$#" -ne 4 ]
  then
    echo "Usage : sh run.sh <gcp project> <subscription name> <gcs bucket name> <optional params>" 
    exit -1
fi

PROJECT_ID=$1
BUCKET="gs://${3}"

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

LAUNCH_PARAMS=" \
 --project=${PROJECT_ID} \
 --runner=DataflowRunner \
 --streaming \
 --stagingLocation=$STAGING_BUCKET/dataflow/staging \
 --tempLocation=$STAGING_BUCKET/dataflow/temp \
 --gcpTempLocation=$STAGING_BUCKET/dataflow/gcptemp \
 --outputTable=${OUTPUT_TABLE} \
 --numWorkers=1 \
 --maxNumWorkers=400 \
 --experiments=min_num_workers=1 \
 --workerMachineType=n2d-standard-4 \
 --createBQTable \
 --autoscalingAlgorithm=THROUGHPUT_BASED \
 --enableStreamingEngine \
 --usePublicIps=false "

#  --network=some-network \
#  --subnetwork=https://www.googleapis.com/compute/v1/projects/some-project/regions/us-central1/subnetworks/some-subnetwork \

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
