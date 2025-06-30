/*
* Copyright 2023 Google LLC. This software is provided as-is, without warranty
* or representation for any use or purpose. Your use of it is subject to your
* agreement with Google.
*/

terraform {
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 6"
    }
  }
}

provider "google" {
  project = var.project
  region  = var.region
}

module "bigtable_instance" {
  count = var.create_bigtable ? 1 : 0
  source = "./bigtable"
  instance_name = "${var.run_name}-instance"
  table_name = var.run_name

  project = var.project
}

module "bigquery_dataset" {
  count = var.create_bigquery ? 1 : 0
  source = "./bigquery"

  project = var.project
  dataset_name = var.run_name
  df_worker = google_service_account.df_worker.email
}

module "pubsub_resources" {
  count = var.create_pubsub ? 1 : 0
  source = "./pubsub"

  project = var.project
  topic_name = var.run_name
  df_worker = google_service_account.df_worker.email
}

module "pubsublite_resources" {
  count = var.create_pubsublite ? 1 : 0
  source = "./pubsublite"

  project = var.project
  topic_name = var.run_name
}

module "kafka_resources" {
  count = var.create_kafka ? 1 : 0
  source = "./kafka"

  project    = var.project
  ssh_user   = var.ssh_user
  ssh_key    = file(pathexpand("~/.ssh/id_rsa.pub"))
  run_name = var.run_name
  subnet = google_compute_subnetwork.subnet_priv.self_link
  df_worker = google_service_account.df_worker.email
}

resource "google_storage_bucket" "staging" {
  project       = var.project
  name          = "${var.run_name}-staging-${var.project}"
  location      = "US-CENTRAL1"
  storage_class = "REGIONAL"
  force_destroy = true

  lifecycle_rule {
    condition {
      age = 7
    }
    action {
      type = "Delete"
    }
  }
  public_access_prevention = "enforced"
}

resource google_service_account "df_worker" {
  project = var.project
  account_id = "${var.run_name}-df-sa"
}

resource google_project_iam_member "df_worker" {
  project = var.project
  role = "roles/dataflow.worker"
  member = "serviceAccount:${google_service_account.df_worker.email}"
}

variable project {}

variable region {
  default = "us-central1"
}

variable create_bigtable {
    type = bool
}

variable create_bigquery {
    type = bool
}

variable create_pubsub {
    type = bool
}

variable create_pubsublite {
    type = bool
}

variable create_kafka {
    type = bool
}

variable ssh_user {}

variable run_name {}

/* ------------------------- */
/*       Outputs             */

output "jmpsrv_ip" {
  value = var.create_kafka ? module.kafka_resources.0.jmpsrv_ip : null
}

output "df_sa" {
  value = google_service_account.df_worker.email
}

output "subnet" {
  value = google_compute_subnetwork.subnet_priv.self_link
}