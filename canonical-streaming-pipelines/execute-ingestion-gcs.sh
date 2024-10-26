#!/bin/bash
set -eu
# Usage : sh run.sh <gcp project> <gcs bucket name> <pipeline name>

if [ "$#" -ne 3 ] && [ "$#" -ne 4 ]
  then
    echo "Usage : sh run.sh <gcp project> <gcs bucket name> <pipeline name> <optional params>"
    exit -1
fi

PROJECT_ID=$1
BUCKET="gs://${2}"

PIPELINE_NAME=$3

STAGING_BUCKET=${BUCKET}

LAUNCH_PARAMS=" \
 --project=${PROJECT_ID} \
 --runner=DataflowRunner \
 --streaming \
 --stagingLocation=$STAGING_BUCKET/dataflow/staging \
 --tempLocation=$STAGING_BUCKET/dataflow/temp \
 --gcpTempLocation=$STAGING_BUCKET/dataflow/gcptemp \
 --maxNumWorkers=50 \
 --experiments=min_num_workers=1 \
 --workerMachineType=n2d-standard-4 \
 --autoscalingAlgorithm=THROUGHPUT_BASED \
 --enableStreamingEngine \
 --usePublicIps=false "

#  --network=some-network \
#  --subnetwork=https://www.googleapis.com/compute/v1/projects/some-project/regions/us-central1/subnetworks/some-subnetwork \

if (( $# == 4 ))
then
  LAUNCH_PARAMS=$LAUNCH_PARAMS$4
fi

mvn compile exec:java -Dexec.mainClass=com.google.cloud.pso.beam.pipelines.${PIPELINE_NAME} -Dexec.cleanupDaemonThreads=false -Dexec.args="${LAUNCH_PARAMS}"
