#!/bin/bash
set -eu

echo "starting data generator"
pushd streaming-data-generator

source run.sh $1 $2 $3 "\
  --className=com.google.cloud.pso.beam.generator.thrift.CompoundEvent \
  --generatorRatePerSec=200000 \
  --maxRecordsPerBatch=4500 \
  --compressionEnabled=true \
  --completeObjects=true "

popd

echo "starting processing pipeline"

pushd canonical-streaming-pipeline

source run.sh $1 $2-sub $3

popd