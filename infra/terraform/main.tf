## Main infrastructure: VPC, EKS (Kubernetes), RDS (Postgres), and ECR (images)
## Costs: Adjust via eks_managed_node_groups
locals {
  name_prefix = "${var.project_name}-${var.environment}"
}

# Networking: VPC with 3 AZs, public+private subnets, single NAT for cost control
module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "~> 5.0"

  name                 = local.name_prefix
  cidr                 = var.vpc_cidr
  azs                  = ["${var.aws_region}a", "${var.aws_region}b", "${var.aws_region}c"]
  public_subnets       = [cidrsubnet(var.vpc_cidr, 4, 0), cidrsubnet(var.vpc_cidr, 4, 1), cidrsubnet(var.vpc_cidr, 4, 2)]
  private_subnets      = [cidrsubnet(var.vpc_cidr, 4, 3), cidrsubnet(var.vpc_cidr, 4, 4), cidrsubnet(var.vpc_cidr, 4, 5)]
  enable_nat_gateway   = true
  single_nat_gateway   = true
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Project     = var.project_name
    Environment = var.environment
  }
}

# Kubernetes: EKS control plane + managed node group
module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 20.0"

  cluster_name    = local.name_prefix
  cluster_version = var.eks_cluster_version
  vpc_id          = module.vpc.vpc_id
  subnet_ids      = module.vpc.private_subnets

  cluster_endpoint_public_access = true

  # Enable IAM role mapping for kubectl access
  enable_cluster_creator_admin_permissions = true

  eks_managed_node_groups = {
    default = {
      instance_types = ["t3.small"]
      desired_size   = 1
      min_size       = 1
      max_size       = 1
    }
  }

  tags = {
    Project     = var.project_name
    Environment = var.environment
  }
}

# Database access: allow Postgres from EKS nodes only
resource "aws_security_group" "rds" {
  name        = "${local.name_prefix}-rds-sg"
  description = "RDS access control"
  vpc_id      = module.vpc.vpc_id

  ingress {
    description      = "Postgres from EKS nodes"
    from_port        = 5432
    to_port          = 5432
    protocol         = "tcp"
    security_groups  = [module.eks.node_security_group_id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Project     = var.project_name
    Environment = var.environment
  }
}

# Database subnet group: private subnets
resource "aws_db_subnet_group" "postgres" {
  name       = "${local.name_prefix}-db-subnets"
  subnet_ids = module.vpc.private_subnets

  tags = {
    Project     = var.project_name
    Environment = var.environment
  }
}

# PostgreSQL RDS instance (managed DB)
resource "aws_db_instance" "postgres" {
  identifier              = "${local.name_prefix}-postgres"
  engine                  = "postgres"
  engine_version          = "16"
  instance_class          = "db.t3.micro"
  allocated_storage       = 20
  db_name                 = "finstream"
  username                = var.db_username
  password                = var.db_password
  skip_final_snapshot     = true
  publicly_accessible     = false
  vpc_security_group_ids  = [aws_security_group.rds.id]
  db_subnet_group_name    = aws_db_subnet_group.postgres.name
  deletion_protection     = false

  tags = {
    Project     = var.project_name
    Environment = var.environment
  }
}

# Container registry: ECR repos for each service (image scanning on push)
locals {
  ecr_services = [
    "api-gateway",
    "wallet-service",
    "transaction-service",
    "frontend",
  ]
}

resource "aws_ecr_repository" "service" {
  for_each = toset(local.ecr_services)
  name     = "${local.name_prefix}-${each.key}"
  force_delete = true
  image_scanning_configuration {
    scan_on_push = true
  }
  image_tag_mutability = "MUTABLE"

  tags = {
    Project     = var.project_name
    Environment = var.environment
  }
}