variable "project" {
  description = "GCP project identifier"
}

variable "region" {
  default = "us-central1"
}

variable "env" {
  default = "devel"
}

/*         Variables         */

variable "topic_name" {
  description = "The topic name to be created"
}

/* ------------------------- */