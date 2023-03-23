/*
* Copyright 2023 Google LLC. This software is provided as-is, without warranty
* or representation for any use or purpose. Your use of it is subject to your
* agreement with Google.
*/

/*       resources           */

data "google_project" "project" {
  project_id = var.project
}

resource "google_pubsub_lite_topic" "psl_topic" {
  name = var.topic_name
  project = data.google_project.project.number
  region = var.region   
  zone = var.zone

  partition_config {
    count = var.partition_count
    capacity {
      publish_mib_per_sec = var.publish_throughput_mbs
      subscribe_mib_per_sec = var.subscribe_throughput_mbs
    }
  }

  retention_config {
    per_partition_bytes = var.bytes_per_partition
  }
}


resource "google_pubsub_lite_subscription" "psl_subscription" {
  name  = "${var.topic_name}-sub"
  topic = google_pubsub_lite_topic.psl_topic.name
  delivery_config {
    delivery_requirement = "DELIVER_AFTER_STORED"
  }
}