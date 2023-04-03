variable "project" {
  description = "GCP project identifier"
}

variable "region" {
  default = "us-central1"
}

variable "zone" {
  default = "us-central1-a"
}

variable "env" {
  default = "devel"
}

/*         Variables         */

variable "topic_name" {
  description = "The topic name to be created"
}

variable reservation_units {
  description = "The reserved capacity in units"
  default = 400
}

variable "partition_count" {
  description = "the number of partitions for the topic"
  default = 50
}

variable "publish_throughput_mbs" {
  description = "The max throughput per partition in MBs for publishing"
  default = 4
}

variable "subscribe_throughput_mbs" {
  description = "The max throughput per partition in MBs for subscribing"
  default = 8
}

variable "bytes_per_partition" {
  description = "The storage bytes per topic partition"
  default = 32212254720
}

/* ------------------------- */