#!/bin/bash
set -eu

if [ "$#" -ne 6 ] 
  then
    echo "Usage : sh tf-apply.sh <gcp project> <topic name> <bucket name> <enable bt (true/false)> <enable bq (true/false)> <enable ps (true/false)>" 
    exit -1
fi

BT_ENABLED=$4
BQ_ENABLED=$5
PS_ENABLED=$6
TOPIC=$2
PROJECT=$1
BUCKET=$3

terraform init && terraform apply \
  -var="create_bigtable=${BT_ENABLED}" \
  -var="create_bigquery=${BQ_ENABLED}" \
  -var="create_pubsub=${PS_ENABLED}"   \
  -var="topic_name=${TOPIC}"           \
  -var="project=${PROJECT}"            \
  -var="staging_bucket_name=${BUCKET}"