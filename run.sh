#!/bin/bash
set -xeu

if [ "$#" -ne 3 ] && [ "$#" -ne 4 ]
  then
    echo "Usage : sh run.sh <gcp project> <topic> <gcs bucket name> <optional params>"
    exit -1
fi


PROJECT=$1
TOPIC=$2
STAGING_BUCKET=$3

LAUNCH_PARAMS=" \
  --project=$PROJECT \
  --stagingLocation=gs://$STAGING_BUCKET/dataflow/staging \
  --tempLocation=gs://$STAGING_BUCKET/dataflow/temp \
  --enableStreamingEngine \
  --numWorkers=400 \
  --maxNumWorkers=400 \
  --runner=DataflowRunner \
  --workerMachineType=n1-standard-8 \
  --usePublicIps=false \
  --region=us-central1 \
  --outputTopic=projects/${PROJECT}/topics/$TOPIC \
  --jobName='nokill-tfe-thriftdatagen-`echo "$TOPIC" | tr _ -`-${USER}' "

#  --maxRecordsPerBatch=250 \

if (( $# == 4 ))
then
  LAUNCH_PARAMS=$LAUNCH_PARAMS$4
fi

mvn compile exec:java -Dexec.mainClass=com.example.dataflow.thrift.generator.SparrowStreamingGenerator -Dexec.cleanupDaemonThreads=false -Dexec.args="$LAUNCH_PARAMS"