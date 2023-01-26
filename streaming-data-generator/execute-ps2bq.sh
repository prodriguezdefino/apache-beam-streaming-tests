#!/bin/bash
set -xeu

if [ "$#" -ne 3 ] && [ "$#" -ne 4 ]
  then
    echo "Usage : sh run.sh <gcp project> <topic> <staging gcs bucket name> <optional params>"
    exit -1
fi


PROJECT=$1
TOPIC=$2
STAGING_BUCKET=$3
REGION="us-central1"

LAUNCH_PARAMS=" \
  --project=$PROJECT \
  --stagingLocation=gs://$STAGING_BUCKET/dataflow/staging \
  --tempLocation=gs://$STAGING_BUCKET/dataflow/temp \
  --enableStreamingEngine \
  --numWorkers=50 \
  --maxNumWorkers=1000 \
  --runner=DataflowRunner \
  --workerMachineType=n1-standard-4 \
  --usePublicIps=false \
  --region=${REGION} \
  --outputTopic=projects/${PROJECT}/topics/${TOPIC} \
  --jobName='streamingdatagen-`echo "$TOPIC" | tr _ -`-${USER}' "

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

mvn compile exec:java -Dexec.mainClass=com.google.cloud.pso.beam.generator.StreamingDataGenerator -Dexec.cleanupDaemonThreads=false $MODIFY_BEAM_VERSION -Dexec.args="$LAUNCH_PARAMS"
