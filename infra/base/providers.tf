provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project   = "urban-alert"
      Service   = "usuarios"
      ManagedBy = "terraform"
      Stack     = "base"
    }
  }
}
