/*
* Copyright 2023 Google LLC. This software is provided as-is, without warranty
* or representation for any use or purpose. Your use of it is subject to your
* agreement with Google.
*/

/*       Local Variables     */
locals {
  bt_labels = {
    "environment"   = "${var.env}"
    "product"       = "bigtable"
  }
}

/*       resources           */
resource "google_bigtable_instance" "instance" {
  name    = "${var.instance_name}"
  project = "${var.project}"

  cluster {
    cluster_id   = "${var.instance_name}-cluster"
    zone         = "${var.zone}"
    storage_type = "SSD"

    autoscaling_config {
      min_nodes  = "${var.min_nodes}"
      max_nodes  = "${var.max_nodes}"
      cpu_target = "${var.cpu_target}"
    }
  }
  labels = local.bt_labels
}

resource "google_bigtable_table" "table" {
  name          = "${var.table_name}"
  instance_name = google_bigtable_instance.instance.name

  column_family {
    family = "${var.column_family_name}"
  }
}

resource "google_bigtable_gc_policy" "policy" {
  instance_name = google_bigtable_instance.instance.name
  table         = google_bigtable_table.table.name
  column_family = "${var.column_family_name}"
  deletion_policy = "ABANDON"


  gc_rules = <<EOF
  {
    "rules": [
      {
        "max_version": 5
      }
    ]
  }
  EOF
}