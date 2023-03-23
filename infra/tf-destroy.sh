#!/bin/bash
set -eu

if [ "$#" -ne 6 ] 
  then
    echo "Usage : sh tf-apply.sh <gcp project> <run name> <enable bt (true/false)> <enable bq (true/false)> <enable ps (true/false)> <enable psl (true/false)>" 
    exit -1
fi

BT_ENABLED=$3
BQ_ENABLED=$4
PS_ENABLED=$5
PSL_ENABLED=$6
NAME=$2
PROJECT=$1

terraform destroy \
  -var="create_bigtable=${BT_ENABLED}" \
  -var="create_bigquery=${BQ_ENABLED}" \
  -var="create_pubsub=${PS_ENABLED}"   \
  -var="create_pubsublite=${PSL_ENABLED}"   \
  -var="run_name=${NAME}"           \
  -var="project=${PROJECT}"            
