#!/usr/bin/env bash

# Copyright 2019 Google LLC. This software is provided as-is, without warranty
# or representation for any use or purpose. Your use of it is subject to your
# agreement with Google.

# Install JRE and pip
wget https://download.java.net/java/GA/jdk21.0.2/f2283984656d49d69e91c558476027ac/13/GPL/openjdk-21.0.2_linux-x64_bin.tar.gz

tar xvf openjdk-21.0.2_linux-x64_bin.tar.gz

mv jdk-21.0.2/ /usr/local/jdk-21

tee -a /etc/profile.d/zjdk21.sh<<EOF
> export JAVA_HOME=/usr/local/jdk-21
> export PATH=\$PATH:\$JAVA_HOME/bin
EOF

rm openjdk-21.0.2_linux-x64_bin.tar.gz

sudo apt-get update && sudo apt-get install -y python3-pip unzip netcat lsof --allow-unauthenticated

cd /tmp \
&& curl -O https://archive.apache.org/dist/kafka/${kafka_version}/kafka_2.12-${kafka_version}.tgz

echo "curl -O https://archive.apache.org/dist/kafka/${kafka_version}/kafka_2.12-${kafka_version}.tgz"

cd /opt \
&& tar xzf /tmp/kafka_2.12-${kafka_version}.tgz
