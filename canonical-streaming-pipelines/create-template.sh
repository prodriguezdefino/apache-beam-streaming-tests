#!/bin/bash
set -xeu

if [ "$#" -ne 5 ] && [ "$#" -ne 6 ]
  then
    echo "Usage : sh create-template.sh <local template file location> <main-class> <name> <gcp project> <gcs bucket name> <gcp region>" 
    exit -1
fi

TEMPLATE_FILE=$1
MAINCLASS=$2
PIPELINE_NAME=$3
GCP_PROJECT=$4
BUCKET_NAME=$5

if [ "$#" -eq 5 ] 
  then
    GCP_REGION="us-central1"
  else
    GCP_REGION=$6
fi

BUILD_TAG=$(date +"%Y-%m-%d_%H-%M-%S")

export TEMPLATE_PATH=${TEMPLATE_FILE} 
export TEMPLATE_IMAGE="gcr.io/${GCP_PROJECT}/${GCP_REGION}/${PIPELINE_NAME}-template:latest"

GCS_TEMPLATE_PATH=gs://${BUCKET_NAME}/template/${PIPELINE_NAME}-template.json  

gcloud auth configure-docker
# Build Docker Image
docker image build -t $TEMPLATE_IMAGE -f ./canonical-streaming-pipelines/Dockerfile --build-arg MAINCLASS=$MAINCLASS .
# Push image to Google Cloud Registry
docker push $TEMPLATE_IMAGE

gcloud dataflow flex-template build $GCS_TEMPLATE_PATH \
  --image "$TEMPLATE_IMAGE" \
  --sdk-language JAVA \
  --metadata-file "${TEMPLATE_FILE}" \
  --additional-user-labels template-name=${PIPELINE_NAME},template-version=${BUILD_TAG} \
  --disable-public-ips \
  --enable-streaming-engine \
  --max-workers=400 \
  --num-workers=1 \
  --staging-location=gs://$BUCKET_NAME/dataflow/staging \
  --temp-location=gs://$BUCKET_NAME/dataflow/temp \
  --worker-machine-type=n2d-standard-4 \
  --additional-experiments enable_recommendations,enable_google_cloud_profiler,enable_google_cloud_heap_sampling,min_num_workers=1
