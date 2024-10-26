/*
* Copyright 2023 Google LLC. This software is provided as-is, without warranty
* or representation for any use or purpose. Your use of it is subject to your
* agreement with Google.
*/

resource "google_service_account" "dataflow_runner_sa" {
  project    = var.project
  account_id = "${var.run_name}-df-sa"
}

module "data_processing_project_membership_roles" {
    source                  = "terraform-google-modules/iam/google//modules/member_iam"
    service_account_address = google_service_account.dataflow_runner_sa.email
    project_id              = var.project
    project_roles           = [
        "roles/dataflow.worker",
        "roles/storage.objectAdmin",
        "roles/bigquery.dataEditor",
        "roles/managedkafka.consumerGroupEditor",
        "roles/managedkafka.client"
    ]
}
