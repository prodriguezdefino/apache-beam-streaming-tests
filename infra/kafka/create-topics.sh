#!/usr/bin/env bash

/opt/kafka_2.12-3.2.0/bin/kafka-topics.sh --create \
--bootstrap-server kafka-0:9092 \
--replication-factor 1 \
--partitions 100 \
--topic tfe

# create default topic 
#if [ "$HOSTNAME" = "Kafka-0" ]
#$KAFKA_ROOT/bin/kafka-topics.sh --create \
#--bootstrap-server localhost:9092 \
#--replication-factor ${default_topic_replication} \
#--partitions ${default_topic_partitions} \
#--topic ${default_topic_name}

/opt/kafka_2.12-3.2.0/bin/kafka-topics.sh --create \
--bootstrap-server localhost:9092 \
--replication-factor 1 \
--partitions 6 \
--topic fhs-output-6

/opt/kafka_2.12-3.2.0/bin/kafka-topics.sh --describe --bootstrap-server localhost:9092 --topic tfe

/opt/kafka_2.12-3.2.0/bin/kafka-broker-api-versions.sh  --bootstrap-server localhost:9092 | grep 9092

/opt/kafka_2.12-3.2.0/bin/kafka-reassign-partitions.sh --bootstrap-server localhost:9092 --reassignment-json-file repartition.json --execute


{"version":1,
  "partitions":[
    {"topic":"tfe","partition":0,"replicas":[25]},
    {"topic":"tfe","partition":1,"replicas":[42]},
    {"topic":"tfe","partition":2,"replicas":[39]},
    {"topic":"tfe","partition":3,"replicas":[41]},
    {"topic":"tfe","partition":4,"replicas":[8]},
    {"topic":"tfe","partition":5,"replicas":[56]},
    {"topic":"tfe","partition":6,"replicas":[40]},
    {"topic":"tfe","partition":7,"replicas":[36]},
    {"topic":"tfe","partition":8,"replicas":[55]},
    {"topic":"tfe","partition":9,"replicas":[6]},
    {"topic":"tfe","partition":10,"replicas":[49]},
    {"topic":"tfe","partition":11,"replicas":[27]},
    {"topic":"tfe","partition":12,"replicas":[50]},
    {"topic":"tfe","partition":13,"replicas":[43]}
  ]
}

kafka-9.us-central1-f.c.pabs-pso-lab.internal:9092 (id: 25 rack: null) -> (
kafka-5.us-central1-f.c.pabs-pso-lab.internal:9092 (id: 42 rack: null) -> (
kafka-10.us-central1-f.c.pabs-pso-lab.internal:9092 (id: 39 rack: null) -> (
kafka-7.us-central1-f.c.pabs-pso-lab.internal:9092 (id: 41 rack: null) -> (
kafka-12.us-central1-f.c.pabs-pso-lab.internal:9092 (id: 8 rack: null) -> (
kafka-3.us-central1-f.c.pabs-pso-lab.internal:9092 (id: 56 rack: null) -> (
kafka-1.us-central1-f.c.pabs-pso-lab.internal:9092 (id: 40 rack: null) -> (
kafka-4.us-central1-f.c.pabs-pso-lab.internal:9092 (id: 36 rack: null) -> (
kafka-2.us-central1-f.c.pabs-pso-lab.internal:9092 (id: 55 rack: null) -> (
kafka-13.us-central1-f.c.pabs-pso-lab.internal:9092 (id: 6 rack: null) -> (
kafka-6.us-central1-f.c.pabs-pso-lab.internal:9092 (id: 49 rack: null) -> (
kafka-0.us-central1-f.c.pabs-pso-lab.internal:9092 (id: 27 rack: null) -> (
kafka-8.us-central1-f.c.pabs-pso-lab.internal:9092 (id: 50 rack: null) -> (
kafka-11.us-central1-f.c.pabs-pso-lab.internal:9092 (id: 43 rack: null) -> (
