#!/bin/bash
set -eu

if [ "$#" -ne 2 ] && [ "$#" -ne 3 ]
  then
    echo "Usage : sh run.sh <gcp project> <staging gcs bucket name> <optional params>"
    exit -1
fi


PROJECT=$1
STAGING_BUCKET="gs://${2}"

LAUNCH_PARAMS=" \
  --project=$PROJECT \
  --stagingLocation=$STAGING_BUCKET/dataflow/staging \
  --tempLocation=$STAGING_BUCKET/dataflow/temp \
  --gcpTempLocation=$STAGING_BUCKET/dataflow/gcptemp \
  --enableStreamingEngine \
  --maxNumWorkers=500 \
  --runner=DataflowRunner \
  --workerMachineType=n1-standard-4 \
  --usePublicIps=false "

#  --network=some-network \
#  --subnetwork=https://www.googleapis.com/compute/v1/projects/some-project/regions/us-central1/subnetworks/some-subnetwork \

if (( $# == 3 ))
then
  LAUNCH_PARAMS=$LAUNCH_PARAMS$3
fi

if [[ -z "${BEAM_VERSION}" ]]; then
  MODIFY_BEAM_VERSION=""
else
  MODIFY_BEAM_VERSION="-Dbeam.version=${BEAM_VERSION}"
fi

mvn compile exec:java -Dexec.mainClass=com.google.cloud.pso.beam.generator.StreamingDataGenerator -Dexec.cleanupDaemonThreads=false $MODIFY_BEAM_VERSION -Dexec.args="$LAUNCH_PARAMS"
