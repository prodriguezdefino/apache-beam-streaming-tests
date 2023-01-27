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

# Beam version var is unset, this will default in the pom.xml definitions
BEAM_VERSION=

echo "starting data generator"
pushd streaming-data-generator

source ./execute-ps2bq.sh $1 $2 $3 "\
  --className=com.google.cloud.pso.beam.generator.thrift.CompoundEvent \
  --generatorRatePerSec=200000 \
  --maxRecordsPerBatch=4500 \
  --compressionEnabled=true \
  --completeObjects=true "$MORE_PARAMS

popd

echo "starting processing pipeline"
pushd canonical-streaming-pipelines

source ./execute-ps2bq.sh $1 $2-sub $3 "\
 --useStorageApiConnectionPool=true \
 --bigQueryWriteMethod=STORAGE_API_AT_LEAST_ONCE \
 --tableDestinationCount=1 "$MORE_PARAMS

popd
