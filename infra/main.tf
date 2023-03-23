
module "bigtable_instance" {
  count = var.create_bigtable ? 1 : 0
  source = "./bigtable"
  instance_name = "${var.run_name}-instance" 
  table_name = var.run_name

  project = var.project
}

module "bigquery_dataset" {
  count = var.create_bigquery ? 1 : 0
  source = "./bigquery"

  project = var.project
  dataset_name = var.run_name
}

module "pubsub_resources" {
  count = var.create_pubsub ? 1 : 0
  source = "./pubsub"

  project = var.project
  topic_name = var.run_name
}

module "pubsublite_resources" {
  count = var.create_pubsublite ? 1 : 0
  source = "./pubsublite"

  project = var.project
  topic_name = var.run_name
}

resource "google_storage_bucket" "staging" {
  project       = var.project 
  name          = "${var.run_name}-staging-${var.project}"
  location      = "US-CENTRAL1"
  storage_class = "REGIONAL"
  force_destroy = true

  lifecycle_rule {
    condition {
      age = 7
    }
    action {
      type = "Delete"
    }
  }
  public_access_prevention = "enforced"
}

variable project {}

variable create_bigtable { 
    type = bool
}

variable create_bigquery { 
    type = bool
}

variable create_pubsub { 
    type = bool
}

variable create_pubsublite { 
    type = bool
}

variable run_name {}
