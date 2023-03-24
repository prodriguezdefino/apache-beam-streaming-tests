#!/bin/bash
set -xeu

if [ "$#" -ne 3 ] && [ "$#" -ne 4 ]
  then
    echo "Usage : sh create-template.sh <local template file location> <gcp project> <gcs bucket name> <gcp region>" 
    exit -1
fi

TEMPLATE_FILE=$1
PIPELINE_NAME=streaming-datagenerator
GCP_PROJECT=$2
BUCKET_NAME=$3

if [ "$#" -eq 4 ] 
  then
    GCP_REGION="us-central1"
  else
    GCP_REGION=$4
fi

BUILD_TAG=$(date +"%Y-%m-%d_%H-%M-%S")
MAINCLASS=com.google.cloud.pso.beam.generator.StreamingDataGenerator

export TEMPLATE_PATH=${TEMPLATE_FILE} 
export TEMPLATE_IMAGE="gcr.io/${GCP_PROJECT}/${GCP_REGION}/${PIPELINE_NAME}-template:latest"

GCS_TEMPLATE_PATH=gs://${BUCKET_NAME}/template/${PIPELINE_NAME}-template.json  

gcloud auth configure-docker
# Build Docker Image
docker image build -t $TEMPLATE_IMAGE -f ./streaming-data-generator/Dockerfile --build-arg MAINCLASS=$MAINCLASS .
# Push image to Google Cloud Registry
docker push $TEMPLATE_IMAGE

gcloud dataflow flex-template build $GCS_TEMPLATE_PATH \
  --image "$TEMPLATE_IMAGE" \
  --sdk-language JAVA \
  --metadata-file "${TEMPLATE_FILE}" \
  --additional-user-labels template-name=${PIPELINE_NAME},template-version=${BUILD_TAG} \
  --disable-public-ips \
  --enable-streaming-engine \
  --max-workers=100 \
  --num-workers=50 \
  --staging-location=gs://$BUCKET_NAME/dataflow/staging \
  --temp-location=gs://$BUCKET_NAME/dataflow/temp \
  --worker-machine-type=n1-standard-4 \
  --additional-experiments enable_recommendations,enable_google_cloud_profiler,enable_google_cloud_heap_sampling,min_num_workers=1
