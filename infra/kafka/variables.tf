variable "project" {
  default = "pabs-pso-lab"
}

variable "region" {
  default = "us-central1"
}

variable "env" {
  default = "devel"
}

/*         Variables         */

variable zone {
  default = "us-central1-f"
}

variable zk_version {
  description = ""
  default     = "3.4.6"
}

variable kafka_version {
  default = "3.2.0"
}

variable zkdata_dir {
  default = "/zkdata"
}

variable zk_machine_type {
  default = "n1-standard-2"
}

variable kafka_machine_type {
  default = "n1-standard-8"
}

variable kafka_log_dir {
  default = "/opt/kafka-logs"
}

/* ------------------------- */