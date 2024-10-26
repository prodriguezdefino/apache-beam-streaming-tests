/*
* Copyright 2023 Google LLC. This software is provided as-is, without warranty
* or representation for any use or purpose. Your use of it is subject to your
* agreement with Google.
*/

locals {
    jmp_template = templatefile("${path.module}/templates/jumpsrv_startup_script.sh.tpl", {
    kafka_version = "${var.kafka_version}"
    topic_name    = var.run_name
  })
  subnet_min = "projects/${var.project}/regions/${var.region}/subnetworks/${google_compute_subnetwork.subnet_priv.name}"
  subnet = "https://www.googleapis.com/compute/v1/${local.subnet_min}"
}

resource "google_compute_project_metadata" "my_ssh_key" {
  project = var.project
  metadata = {
    ssh-keys = <<EOF
      ${var.ssh_user}:${var.ssh_key}

    EOF
  }
}

resource "google_compute_instance" "jmp" {
  project                   = var.project
  name                      = "jmp-srv-${var.run_name}"
  machine_type              = "n1-standard-4"
  zone                      = "${var.zone}"
  tags                      = ["allow-ssh"]
  allow_stopping_for_update = true

  boot_disk {
    initialize_params {
      image = "debian-cloud/debian-12"
    }
  }

  network_interface {
    subnetwork         = local.subnet
    subnetwork_project = var.project
    access_config {
      // Ephemeral public IP
    }
  }

  service_account {
    # Google recommends custom service accounts that have cloud-platform scope and permissions granted via IAM Roles.
    scopes = ["cloud-platform"]
  }

  metadata_startup_script = local.jmp_template
}
