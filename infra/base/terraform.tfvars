region = "us-east-2"
name   = "urban-alert-usuarios"

db_name                     = "urbanalert_users"
db_app_user                 = "users_app"
db_engine_version           = "17"
db_instance_class           = "db.t4g.micro"
db_allocated_storage_gb     = 20
db_max_allocated_storage_gb = 50
db_backup_retention_days    = 7

ecr_keep_images    = 20
log_retention_days = 30

migrate_task_cpu    = 512
migrate_task_memory = 1024
