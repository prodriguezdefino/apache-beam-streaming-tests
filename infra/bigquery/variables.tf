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

variable "dataset_name" {
  description = "The dataset name to be created"
}

/* ------------------------- */