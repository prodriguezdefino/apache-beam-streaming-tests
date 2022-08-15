#!/usr/bin/env bash

# Copyright 2019 Google LLC. This software is provided as-is, without warranty
# or representation for any use or purpose. Your use of it is subject to your
# agreement with Google.

# Install JRE and pip
sudo apt-get update && sudo apt-get install -y python-pip unzip default-jre --allow-unauthenticated 

cd /tmp \
&& curl -O https://archive.apache.org/dist/kafka/${kafka_version}/kafka_2.12-${kafka_version}.tgz

echo "curl -O https://archive.apache.org/dist/kafka/${kafka_version}/kafka_2.12-${kafka_version}.tgz"

cd /opt \
&& tar xzf /tmp/kafka_2.12-${kafka_version}.tgz

pip install kafka thrift

echo "
watch -n3 /opt/kafka_2.12-3.2.0/bin/kafka-consumer-groups.sh --bootstrap-server \$1:9092 --describe --group \$2" > /opt/check-partitions.sh

chmod 777 /opt/check-partitions.sh