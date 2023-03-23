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

variable "partition_count" {
  description = "the number of partitions for the topic"
  default = 10
}

variable "publish_throughput_mbs" {
  description = "The max throughput in MBs for publishing"
  default = 40
}

variable "subscribe_throughput_mbs" {
  description = "The max throughput in MBs for subscribing"
  default = 80
}

variable "bytes_per_partition" {
  description = "The storage bytes per topic partition"
  default = 32212254720
}

/* ------------------------- */