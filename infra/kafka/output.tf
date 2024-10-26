/*
* Copyright 2019 Google LLC. This software is provided as-is, without warranty
* or representation for any use or purpose. Your use of it is subject to your
* agreement with Google.
*/

/* ------------------------- */
/*       Outputs             */

output "jmpsrv_ip" {
  value = google_compute_instance.jmp.network_interface.0.access_config.0.nat_ip
}

output "df_sa" {
  value = google_service_account.dataflow_runner_sa.email
}

output "subnet" {
  value = google_compute_subnetwork.subnet_priv.self_link
}