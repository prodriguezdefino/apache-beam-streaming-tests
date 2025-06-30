/*
* Copyright 2023 Google LLC. This software is provided as-is, without warranty
* or representation for any use or purpose. Your use of it is subject to your
* agreement with Google.
*/

variable "project" {
  description = "GCP project identifier"
}

variable "ssh_user" {}
variable "ssh_key" {}
variable "run_name" {}
variable subnet {}
variable df_worker {}

variable "region" {
  default = "us-central1"
}

variable "env" {
  default = "devel"
}

/*         Variables         */

variable zone {
  default = "us-central1-a"
}

variable zk_version {
  description = ""
  default     = "3.4.12"
}

variable zk_node_count {
  default = "3"
}

variable kafka_node_count {
  default = "20"
}

variable kafka_version {
  default = "3.2.3"
}

variable zkdata_dir {
  default = "/zkdata"
}

variable zk_machine_type {
  default = "n1-standard-8"
}

variable kafka_machine_type {
  default = "n1-standard-16"
}

variable kafka_log_dir {
  default = "/opt/kafka-logs"
}

/* ------------------------- */