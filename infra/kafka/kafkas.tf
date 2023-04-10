/*
* Copyright 2019 Google LLC. This software is provided as-is, without warranty
* or representation for any use or purpose. Your use of it is subject to your
* agreement with Google.
*/

/*       Local Variables     */

locals {
  kf_labels = {
    "environment"   = "${var.env}"
    "product"       = "kafka-cluster"
  }

  zk_template = templatefile("${path.module}/templates/zookeeper_startup_script.sh.tpl", {
    zk_version = "${var.zk_version}"
    zk1_ip     = "${google_compute_address.zk_int_addresses.0.address}"
    zk2_ip     = "${google_compute_address.zk_int_addresses.1.address}"
    zk3_ip     = "${google_compute_address.zk_int_addresses.2.address}"
    zkdata_dir = "${var.zkdata_dir}"
  })

  kafka_template = templatefile("${path.module}/templates/kafka_startup_script.sh.tpl", {
    kafka_version = "${var.kafka_version}"
    zk1_ip        = "${google_compute_address.zk_int_addresses.0.address}"
    zk2_ip        = "${google_compute_address.zk_int_addresses.1.address}"
    zk3_ip        = "${google_compute_address.zk_int_addresses.2.address}"
    kafka_log_dir = "${var.kafka_log_dir}"
    run_name   = var.run_name
  })

  jmp_template = templatefile("${path.module}/templates/jumpsrv_startup_script.sh.tpl", {
    kafka_version = "${var.kafka_version}"
    topic_name    = var.run_name
  })
}

resource "google_service_account" "dataflow_runner_sa" {
  project    = var.project
  account_id = "${var.run_name}-df-sa"
}

module "data_processing_project_membership_roles" {
  source                  = "terraform-google-modules/iam/google//modules/member_iam"
  service_account_address = google_service_account.dataflow_runner_sa.email
  project_id              = var.project
  project_roles           = ["roles/dataflow.worker", "roles/storage.objectAdmin", "roles/bigquery.dataEditor"]
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
  tags                      = ["zk-clients", "kafka-clients", "kafka", "allow-ssh"]
  allow_stopping_for_update = true

  boot_disk {
    initialize_params {
      image = "debian-cloud/debian-11"
    }
  }

  network_interface {
    subnetwork         = "${google_compute_subnetwork.subnet_priv.name}"
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

/*    Kafka setup resources  */

resource "google_compute_instance" "kafkas" {
  count                     = var.kafka_node_count
  project                   = var.project
  name                      = "kafka-${var.run_name}-${count.index}"
  machine_type              = "${var.kafka_machine_type}"
  zone                      = "${var.zone}"
  tags                      = ["zk-clients", "kafka-clients", "kafka", "allow-internal-ssh"]
  allow_stopping_for_update = true

  boot_disk {
    initialize_params {
      size  = 512
      type  = "pd-ssd"
      image = "debian-cloud/debian-11"
    }
  }

  network_interface {
    subnetwork         = "${google_compute_subnetwork.subnet_priv.name}"
    subnetwork_project = var.project
    network_ip         = "${google_compute_address.kafka_int_addresses.*.address[count.index]}"
  }

  metadata_startup_script = local.kafka_template
}

/* ------------------------- */
/*    Zookeeper resources    */

resource "google_compute_instance" "zks" {
  count                     = var.zk_node_count
  project                   = var.project
  name                      = "zk-${var.run_name}-${count.index}"
  machine_type              = "${var.zk_machine_type}"
  zone                      = "${var.zone}"
  tags                      = ["zk", "allow-internal-ssh"]
  allow_stopping_for_update = true

  boot_disk {
    initialize_params {
      size  = 128
      type  = "pd-ssd"
      image = "debian-cloud/debian-11"
    }
  }

  network_interface {
    subnetwork         = "${google_compute_subnetwork.subnet_priv.name}"
    subnetwork_project = var.project
    network_ip         = "${google_compute_address.zk_int_addresses.*.address[count.index]}"
  }

  metadata_startup_script = local.zk_template
}
