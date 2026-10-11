# One-off task that applies the Flyway migrations and the application role (profile aws,migrate) from inside the
# VPC, as the RDS master user, with the same image the service will run. Launched by scripts/run-schema-task.sh
# before every deployment (spec RF-008, RF-010; plan D-002).
locals {
  migrate_container = "migrate"
}

resource "aws_ecs_task_definition" "migrate" {
  family                   = "${var.name}-migrate"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.migrate_task_cpu
  memory                   = var.migrate_task_memory
  execution_role_arn       = aws_iam_role.execution.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "X86_64"
  }

  container_definitions = jsonencode([{
    name      = local.migrate_container
    image     = "${aws_ecr_repository.this.repository_url}:${var.image_tag}"
    essential = true

    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = "aws,migrate" },
      { name = "DB_APP_USER", value = var.db_app_user },
    ]

    secrets = [
      { name = "DB_URL", valueFrom = "${aws_secretsmanager_secret.app.arn}:jdbc_url::" },
      { name = "DB_USERNAME", valueFrom = "${aws_db_instance.this.master_user_secret[0].secret_arn}:username::" },
      { name = "DB_PASSWORD", valueFrom = "${aws_db_instance.this.master_user_secret[0].secret_arn}:password::" },
      { name = "DB_APP_PASSWORD", valueFrom = "${aws_secretsmanager_secret.app.arn}:password::" },
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.migrate.name
        awslogs-region        = var.region
        awslogs-stream-prefix = local.migrate_container
      }
    }
  }])

  # The secret value must exist before a task can read it.
  depends_on = [aws_secretsmanager_secret_version.app]
}
