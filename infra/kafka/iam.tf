/*
* Copyright 2023 Google LLC. This software is provided as-is, without warranty
* or representation for any use or purpose. Your use of it is subject to your
* agreement with Google.
*/

module "data_processing_project_membership_roles" {
    source                  = "terraform-google-modules/iam/google//modules/member_iam"
    service_account_address = var.df_worker
    project_id              = var.project
    project_roles           = [
        "roles/storage.objectAdmin",
        "roles/managedkafka.consumerGroupEditor",
        "roles/managedkafka.client"
    ]
}
