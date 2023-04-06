#!/usr/bin/env bash

# Copyright 2019 Google LLC. This software is provided as-is, without warranty
# or representation for any use or purpose. Your use of it is subject to your
# agreement with Google.

# Install JRE and pip
sudo apt-get update && sudo apt-get install -y python3-pip unzip openjdk-17-jre-headless netcat lsof --allow-unauthenticated 

cd /tmp \
&& curl -O https://archive.apache.org/dist/kafka/${kafka_version}/kafka_2.12-${kafka_version}.tgz

echo "curl -O https://archive.apache.org/dist/kafka/${kafka_version}/kafka_2.12-${kafka_version}.tgz"

cd /opt \
&& tar xzf /tmp/kafka_2.12-${kafka_version}.tgz

pip install kafka thrift

echo "
watch -n3 /opt/kafka_2.12-${kafka_version}/bin/kafka-consumer-groups.sh --bootstrap-server \$1:9092 --describe --group \$2" > /opt/check-partitions.sh

chmod 777 /opt/check-partitions.sh

# wait for a kafka to come up online
echo "waiting for online kafka"
while ! nc -z kafka-0 9092 ; do sleep 1 ; done
echo "found kafka online"

/opt/kafka_2.12-${kafka_version}/bin/kafka-topics.sh --create \
--bootstrap-server kafka-0:9092 \
--replication-factor 1 \
--partitions 200 \
--topic ${topic_name}