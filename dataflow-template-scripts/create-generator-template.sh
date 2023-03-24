#!/bin/bash
set -xeu

if [ "$#" -ne 3 ] && [ "$#" -ne 4 ]
  then
    echo "Usage : sh create-agg-template.sh <local template file location> <gcp project> <gcs bucket name> <gcp region>" 
    exit -1
fi

TEMPLATE_FILE=$1
GCP_PROJECT=$2
BUCKET=$3

if [ "$#" -eq 3 ] 
  then
    GCP_REGION="us-central1"
  else
    GCP_REGION=$4
fi

source ./streaming-data-generator/create-template.sh $TEMPLATE_FILE $GCP_PROJECT $BUCKET $GCP_REGION