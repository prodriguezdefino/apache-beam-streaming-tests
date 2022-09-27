#!/bin/bash

# Usage : gradual_rampup.sh <num_publishers> <sleep_interval> <gcp project> <gcs bucket name> <topic name> <additional params>
# The script will start <num_publishers> pipelines which publish data to PubSub.
# It will start the first publisher immediately, and will wait for <sleep_interval> seconds
# before starting the next one.

SLEEP_INTERVAL=$2
NUM_PUBLISHERS=$1
PUBLISHER_NUM=0

while [ $PUBLISHER_NUM != $NUM_PUBLISHERS ]
do
  (( PUBLISHER_NUM++ ))
  echo "=== Starting publisher $PUBLISHER_NUM of $NUM_PUBLISHERS ==="
  
  ./run.sh $3 $4 $5 $6
  
  echo "Sleeping for $SLEEP_INTERVAL seconds"
  sleep $SLEEP_INTERVAL
done