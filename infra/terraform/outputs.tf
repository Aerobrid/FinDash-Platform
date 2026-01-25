output "cluster_name" {
  value       = module.eks.cluster_name
  description = "EKS cluster name"
}

output "cluster_endpoint" {
  value       = module.eks.cluster_endpoint
  description = "EKS cluster endpoint"
}

output "ecr_repository_urls" {
  value       = { for k, r in aws_ecr_repository.service : k => r.repository_url }
  description = "Map of ECR repo names to URLs"
}

output "rds_endpoint" {
  value       = aws_db_instance.postgres.address
  description = "PostgreSQL RDS endpoint hostname"
}