
module "bigtable_instance" {
  count = var.create_bigtable ? 1 : 0
  source = "./bigtable"

  project = var.project
}

module "bigquery_dataset" {
  count = var.create_bigquery ? 1 : 0
  source = "./bigquery"

  project = var.project
  dataset_name = var.topic_name
}

module "pubsub_resources" {
  count = var.create_pubsub ? 1 : 0
  source = "./pubsub"

  project = var.project
  topic_name = var.topic_name
}

resource "google_storage_bucket" "staging" {
  project       = var.project 
  name          = var.staging_bucket_name
  location      = "US"
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

variable topic_name {}

variable staging_bucket_name {}
