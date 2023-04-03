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

variable zone {
  default = "us-central1-f"
}

variable instance_name {
  default = "aggregations-instance"
}

variable table_name {
  default = "aggregations"
}

variable column_family_name {
  default = "aggregation_results"
}

variable min_nodes {
  default = "1"
}

variable max_nodes {
  default = "10"
}

variable cpu_target {
  default = "80"
}

/* ------------------------- */