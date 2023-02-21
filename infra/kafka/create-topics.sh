#!/usr/bin/env bash

TOPIC_NAME = some_topic

/opt/kafka_2.12-3.2.0/bin/kafka-topics.sh --create \
--bootstrap-server kafka-0:9092 \
--replication-factor 1 \
--partitions 100 \
--topic ${TOPIC_NAME}

# create default topic 
#if [ "$HOSTNAME" = "Kafka-0" ]
#$KAFKA_ROOT/bin/kafka-topics.sh --create \
#--bootstrap-server localhost:9092 \
#--replication-factor ${default_topic_replication} \
#--partitions ${default_topic_partitions} \
#--topic ${default_topic_name}

#/opt/kafka_2.12-3.2.0/bin/kafka-topics.sh --describe --bootstrap-server localhost:9092 --topic ${TOPIC_NAME}

#/opt/kafka_2.12-3.2.0/bin/kafka-broker-api-versions.sh  --bootstrap-server localhost:9092 | grep 9092

#/opt/kafka_2.12-3.2.0/bin/kafka-reassign-partitions.sh --bootstrap-server localhost:9092 --reassignment-json-file repartition.json --execute
