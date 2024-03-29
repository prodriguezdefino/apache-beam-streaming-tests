resource "google_compute_network" "net_priv" {
  name                    = "kafka-net-${var.run_name}"
  auto_create_subnetworks = false
  project                 = "${var.project}"
}

resource "google_compute_subnetwork" "subnet_priv" {
  name                     = "${var.region}-subnet-${var.run_name}"
  project                  = "${var.project}"
  region                   = "${var.region}"
  private_ip_google_access = true
  ip_cidr_range            = "10.0.0.0/24"
  network                  = "${google_compute_network.net_priv.self_link}"
}

resource "google_compute_address" "zk_int_addresses" {
  count        = var.zk_node_count
  project      = var.project
  name         = "zk-address-${var.run_name}-${count.index}"
  subnetwork   = "${google_compute_subnetwork.subnet_priv.name}"
  address_type = "INTERNAL"
  region       = "${var.region}"
}

resource "google_compute_address" "kafka_int_addresses" {
  count        = var.kafka_node_count
  project      = var.project
  name         = "kafka-address-${var.run_name}-${count.index}"
  subnetwork   = "${google_compute_subnetwork.subnet_priv.name}"
  address_type = "INTERNAL"
  region       = "${var.region}"
}

resource "google_compute_router" "router" {
  name    = "net-router-${var.run_name}"
  project = var.project
  region  = "${google_compute_subnetwork.subnet_priv.region}"
  network = "${google_compute_network.net_priv.self_link}"

  bgp {
    asn = 64514
  }
}

resource "google_compute_router_nat" "nat" {
  name                               = "nat-${var.run_name}"
  project                            = var.project
  router                             = "${google_compute_router.router.name}"
  region                             = "${var.region}"
  nat_ip_allocate_option             = "AUTO_ONLY"
  source_subnetwork_ip_ranges_to_nat = "ALL_SUBNETWORKS_ALL_IP_RANGES"
}


resource "google_compute_firewall" "allow_ssh" {
  name    = "allow-ssh-${var.run_name}"
  project                            = var.project
  network = "${google_compute_network.net_priv.name}"

  allow {
    protocol = "tcp"
    ports    = ["22"]
  }

  source_ranges = ["0.0.0.0/0"]
  target_tags   = ["allow-ssh"]
}

resource "google_compute_firewall" "allow_internal_ssh" {
  name    = "allow-internal-ssh-${var.run_name}"
  project                            = var.project
  network = "${google_compute_network.net_priv.name}"

  allow {
    protocol = "tcp"
    ports    = ["22"]
  }

  source_ranges = ["10.0.0.0/24"]
  target_tags   = ["allow-internal-ssh", "dataflow"]
}

resource "google_compute_firewall" "zk_comms" {
  name    = "zk-comms-${var.run_name}"
  project                            = var.project
  network = "${google_compute_network.net_priv.name}"

  allow {
    protocol = "icmp"
  }

  allow {
    protocol = "tcp"
    ports    = ["2888", "3888"]
  }

  source_tags = ["zk"]
  target_tags = ["zk"]
}

resource "google_compute_firewall" "zk_clients" {
  name    = "zk-clients-${var.run_name}"
  project                            = var.project
  network = "${google_compute_network.net_priv.name}"

  allow {
    protocol = "icmp"
  }

  allow {
    protocol = "tcp"
    ports    = ["2181"]
  }

  source_tags = ["zk-clients"]
  target_tags = ["zk"]
}

resource "google_compute_firewall" "kafka_clients_tag" {
  name    = "kafka-clients-tag-${var.run_name}"
  project                            = var.project
  network = "${google_compute_network.net_priv.name}"

  allow {
    protocol = "icmp"
  }

  allow {
    protocol = "tcp"
    ports    = ["9092"]
  }

  source_tags = ["kafka-clients"]
  target_tags = ["kafka"]
}

resource "google_compute_firewall" "dataflow_tag" {
  name    = "dataflow-tag-${var.run_name}"
  project                            = var.project
  network = "${google_compute_network.net_priv.name}"

  allow {
    protocol = "tcp"
    ports    = ["1-65535"]
  }

  source_tags = ["dataflow"]
  target_tags = ["dataflow"]
}

resource "google_compute_firewall" "kafka_clients_sa" {
  name    = "kafka-clients-sa-${var.run_name}"
  project                            = var.project
  network = "${google_compute_network.net_priv.name}"

  allow {
    protocol = "icmp"
  }

  allow {
    protocol = "tcp"
    ports    = ["9092"]
  }

  source_service_accounts = [google_service_account.dataflow_runner_sa.email]
}
