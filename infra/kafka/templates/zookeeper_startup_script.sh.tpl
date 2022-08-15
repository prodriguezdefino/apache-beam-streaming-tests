#!/usr/bin/env bash

# Copyright 2019 Google LLC. This software is provided as-is, without warranty
# or representation for any use or purpose. Your use of it is subject to your
# agreement with Google.

# Configure Second disk for zkdata directory
{ [ mkdir -p /tmp/testmount && sudo mount /dev/sdb1 /tmp/testmount && sudo umount /tmp/testmount && sudo rm -rf /tmp/testmount ]; } \
|| { sudo parted --script /dev/sdb mklabel gpt && sudo parted --script --align optimal /dev/sdb mkpart primary ext4 0% 100% && sudo mkfs.ext4 /dev/sdb1 && sudo mkdir -p ${zkdata_dir} && sudo mount /dev/sdb1 ${zkdata_dir}; }

# Install Default JRE
sudo apt-get update && sudo apt-get install -y default-jre supervisor --allow-unauthenticated

cd /tmp \
  && curl -O https://archive.apache.org/dist/zookeeper/zookeeper-${zk_version}/zookeeper-${zk_version}.tar.gz

echo "curl -O https://archive.apache.org/dist/zookeeper/zookeeper-${zk_version}/zookeeper-${zk_version}.tar.gz"

cd /opt \
  && tar xzf /tmp/zookeeper-${zk_version}.tar.gz

# Assuming that the zookeeper nodes belong to same cidr range because we use last octet as ZK ID
# They need to be unique in the range (1 - 255)
zk_id="$(curl -s 'http://metadata.google.internal/computeMetadata/v1/instance/network-interfaces/0/ip' -H 'Metadata-Flavor: Google' | awk -F. '{print $NF}')"
zk_id1=$(echo "${zk1_ip}" | awk -F. '{print $NF}')
zk_id2=$(echo "${zk2_ip}" | awk -F. '{print $NF}')
zk_id3=$(echo "${zk3_ip}" | awk -F. '{print $NF}')  

ZK_ROOT=/opt/zookeeper-${zk_version}

echo "# The number of milliseconds of each tick 
tickTime=2000
# The number of ticks that the initial
# synchronization phase can take  
initLimit=10
# The number of ticks that can pass between
# sending a request and getting an acknowledgement
syncLimit=5
# the directory where the snapshot is stored.
# do not use /tmp for storage, /tmp here is just
# example sakes. 
dataDir=${zkdata_dir}
# the port at which the clients will connect
clientPort=2181
# the maximum number of client connections.
# increase this if you need to handle more clients
#maxClientCnxns=60
#
# Be sure to read the maintenance section of the
# administrator guide before turning on autopurge.
#
# http://zookeeper.apache.org/doc/current/zookeeperAdmin.html#sc_maintenance
#
# The number of snapshots to retain in dataDir
#autopurge.snapRetainCount=3
# Purge task interval in hours
# Set to "0" to disable auto purge feature.
#autopurge.purgeInterval=1 
server.$zk_id1=${zk1_ip}:2888:3888
server.$zk_id2=${zk2_ip}:2888:3888
server.$zk_id3=${zk3_ip}:2888:3888" > $ZK_ROOT/conf/zoo.cfg

echo $zk_id > ${zkdata_dir}/myid

# Start Zookeeper
echo "#!/usr/bin/env bash
$ZK_ROOT/bin/zkServer.sh start" > $ZK_ROOT/bin/zk-start.sh

chmod 777 $ZK_ROOT/bin/zk-start.sh

# setup supervisord
echo "[program:zk]
command=$ZK_ROOT/bin/zk-start.sh
autostart=true
autorestart=true
stderr_logfile=/var/log/zk.err.log
stdout_logfile=/var/log/zk.out.log" > /etc/supervisor/conf.d/zk.conf

supervisorctl reread
supervisorctl reload
