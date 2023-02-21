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
  })

  jmp_template = templatefile("${path.module}/templates/jumpsrv_startup_script.sh.tpl", {
    kafka_version = "${var.kafka_version}"
  })
}

resource "google_compute_instance" "jmp" {
  name                      = "jmp-srv"
  machine_type              = "n1-standard-1"
  zone                      = "${var.zone}"
  tags                      = ["zk-clients", "kafka-clients", "kafka", "allow-ssh"]
  allow_stopping_for_update = true

  boot_disk {
    initialize_params {
      image = "debian-cloud/debian-10"
    }
  }

  network_interface {
    subnetwork = "${google_compute_subnetwork.subnet_priv.name}"
    //access_config {
      // Ephemeral public IP
    //}
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
  name                      = "kafka-${count.index}"
  machine_type              = "${var.kafka_machine_type}"
  zone                      = "${var.zone}"
  tags                      = ["zk-clients", "kafka-clients", "kafka", "allow-ssh"]
  allow_stopping_for_update = true

  boot_disk {
    initialize_params {
      size  = 100
      type  = "pd-ssd"
      image = "debian-cloud/debian-10"
    }
  }

  network_interface {
    subnetwork = "${google_compute_subnetwork.subnet_priv.name}"
    network_ip = "${google_compute_address.kafka_int_addresses.*.address[count.index]}"
  }

  metadata_startup_script = local.kafka_template
  depends_on              = [google_compute_instance.zks]
}

/* ------------------------- */


/*    Zookeeper resources    */

resource "google_compute_disk" "zk_data_disks" {
  count = var.zk_node_count
  name  = "zk-data-${count.index}"
  type  = "pd-ssd"
  zone  = "${var.zone}"
  size  = 50
}

resource "google_compute_instance" "zks" {
  count                     = var.zk_node_count
  name                      = "zk-${count.index}"
  machine_type              = "${var.zk_machine_type}"
  zone                      = "${var.zone}"
  tags                      = ["zk", "allow-ssh"]
  allow_stopping_for_update = true

  boot_disk {
    initialize_params {
      image = "debian-cloud/debian-10"
    }
  }

  attached_disk {
    source = "${google_compute_disk.zk_data_disks.*.name[count.index]}"
    mode   = "READ_WRITE"
  }

  network_interface {
    subnetwork = "${google_compute_subnetwork.subnet_priv.name}"
    network_ip = "${google_compute_address.zk_int_addresses.*.address[count.index]}"
  }

  metadata_startup_script = local.zk_template
}



/* ------------------------- */



