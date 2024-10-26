/*
* Copyright 2023 Google LLC. This software is provided as-is, without warranty
* or representation for any use or purpose. Your use of it is subject to your
* agreement with Google.
*/

resource "google_managed_kafka_cluster" "cluster" {
    cluster_id = "kafka-${var.run_name}"
    project    = var.project
    location   = var.region

    capacity_config {
        vcpu_count = 200
        memory_bytes = 858993459200
    }

    gcp_config {
        access_config {
            network_configs {
                subnet = "projects/${var.project}/regions/${var.region}/subnetworks/${google_compute_subnetwork.subnet_priv.name}"
            }
        }
    }

    rebalance_config {
        mode = "AUTO_REBALANCE_ON_SCALE_UP"
    }

    provider = google-beta
}

resource "google_managed_kafka_topic" "topic" {
    project            = var.project
    topic_id           = var.run_name
    cluster            = google_managed_kafka_cluster.cluster.cluster_id
    location           = var.region
    partition_count    = 1000
    replication_factor = 1
    configs            = {
        "retention.ms"           = "3600000"
        "max.message.bytes"      = "6291456"
        "message.timestamp.type" = "LogAppendTime"
    }

    provider = google-beta
}