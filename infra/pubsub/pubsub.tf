/*
* Copyright 2023 Google LLC. This software is provided as-is, without warranty
* or representation for any use or purpose. Your use of it is subject to your
* agreement with Google.
*/

/*       Local Variables     */
locals {
  topic_labels = {
    "environment"   = "${var.env}"
    "product"       = "pubsub"
  }
}

/*       resources           */

resource "google_pubsub_topic" "topic" {
  project = var.project
  name = var.topic_name
}

resource "google_pubsub_subscription" "subscription" {
  project = var.project
  name  = "${var.topic_name}-sub"
  topic = google_pubsub_topic.topic.name

  labels = local.topic_labels
}