/*
* Copyright 2023 Google LLC. This software is provided as-is, without warranty
* or representation for any use or purpose. Your use of it is subject to your
* agreement with Google.
*/

/*       Local Variables     */
locals {
  bq_labels = {
    "environment"   = "${var.env}"
    "product"       = "bigquery"
  }
}

/*       resources           */

resource "google_bigquery_dataset" "dataset" {
  project                     = var.project
  dataset_id                  = var.dataset_name
  friendly_name               = var.dataset_name
  description                 = "Dataset created for DF ingestion tests"
  location                    = "US"
  default_table_expiration_ms = 604800000
  labels                      = local.bq_labels
  max_time_travel_hours       = 168
  delete_contents_on_destroy  = true
}

resource "google_bigquery_dataset_iam_member" "df_worker_editor" {
  dataset_id = google_bigquery_dataset.dataset.dataset_id
  role       = "roles/bigquery.dataEditor"
  member     = "serviceAccount:${var.df_worker}"
}