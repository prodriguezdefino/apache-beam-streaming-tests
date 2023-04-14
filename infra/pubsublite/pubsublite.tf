/*
* Copyright 2023 Google LLC. This software is provided as-is, without warranty
* or representation for any use or purpose. Your use of it is subject to your
* agreement with Google.
*/

/*       resources           */

data "google_project" "project" {
  project_id = var.project
}

resource "google_pubsub_lite_reservation" "reservation" {
  name = var.topic_name
  project = data.google_project.project.number
  region = var.region
  throughput_capacity = var.reservation_units
}

resource "google_pubsub_lite_topic" "psl_topic" {
  name = var.topic_name
  project = data.google_project.project.number
  region = var.region   
  zone = var.zone

  partition_config {
    count = var.partition_count
  }

  reservation_config {
    throughput_reservation = google_pubsub_lite_reservation.reservation.name
  }

  retention_config {
    per_partition_bytes = var.bytes_per_partition
  }
}

resource "google_pubsub_lite_subscription" "psl_subscription" {
  name  = "${var.topic_name}-sub"
  topic = google_pubsub_lite_topic.psl_topic.name
  project = data.google_project.project.number
  region = var.region   
  zone = var.zone

  delivery_config {
    delivery_requirement = "DELIVER_AFTER_STORED"
  }
}