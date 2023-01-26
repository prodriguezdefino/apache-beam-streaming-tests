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


echo "starting data generator"
pushd streaming-data-generator

source ./execute-ps.sh $1 $2 $3 "\
  --className=com.google.cloud.pso.beam.generator.thrift.CompoundEvent \
  --generatorRatePerSec=200000 \
  --maxRecordsPerBatch=4500 \
  --compressionEnabled=true \
  --completeObjects=true "$MORE_PARAMS

popd

echo "starting processing pipeline"
pushd canonical-streaming-pipelines

source ./execute-ps.sh $1 $2-sub $3 $MORE_PARAMS

popd