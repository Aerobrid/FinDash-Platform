# FinDash Architecture & Deployment Guide

## Table of Contents

1. [Repository Structure](#repository-structure)
2. [What Works & What Doesn't](#what-works--what-doesnt)
3. [Security & Secrets](#security--secrets)
4. [Deployment Architecture](#deployment-architecture)
5. [Infrastructure-as-Code](#infrastructure-as-code)
6. [Ansible Deployment](#ansible-deployment)
7. [Troubleshooting](#troubleshooting)

---

## Repository Structure

### For Learning Projects

```
java-app/                          # Single repo (fine for learning)
├── api-gateway/                   # Spring Cloud Gateway service
│   ├── src/main/java/            # Gateway routing logic
│   ├── src/main/resources/       # application.yml with CORS config
│   └── pom.xml
├── wallet-service/               # User accounts & balance service
│   ├── src/main/java/
│   └── pom.xml
├── transaction-service/          # Payment processing service
│   ├── src/main/java/
│   └── pom.xml
├── frontend/                      # Vue 3 + Vite UI
│   ├── src/                      # Vue components
│   ├── Dockerfile
│   └── package.json
├── common/                        # Shared libraries & gRPC definitions
│   └── src/main/proto/           # gRPC service definitions
├── infra/                         # ALL infrastructure code
│   ├── terraform/                # AWS provisioning
│   │   ├── main.tf              # VPC, EKS, RDS, ECR
│   │   ├── variables.tf
│   │   ├── outputs.tf
│   │   └── bootstrap/           # State management (S3 + DynamoDB)
│   ├── kubernetes/              # K8s manifests
│   │   └── base/                # Deployments, Services, ConfigMaps
│   └── ansible/                 # Deployment automation
│       └── deploy.yml           # Build, push, deploy playbook
├── docker-compose.yml           # Local development setup
├── pom.xml                      # Root Maven config
├── .gitignore                   # Security: excludes secrets
├── README.md                    # Project overview
└── ARCHITECTURE.md              # This file
```

### For Production Teams (Recommended Separation)

```
finstream-app/                     # App code repo
├── api-gateway/
├── wallet-service/
├── transaction-service/
├── frontend/
├── common/
├── docker-compose.yml
├── pom.xml
├── README.md
└── .gitignore

finstream-infra/                   # Infrastructure repo (separate)
├── terraform/
├── kubernetes/
├── ansible/
└── README.md

finstream-docs/                    # Optional: Documentation repo
└── ARCHITECTURE.md
```

**Why separate in production?**
- Different deploy cadences
- Different access controls
- Different CI/CD pipelines
- Easier compliance & audit trails

---

## What Works & What Doesn't

### What Works

| Component | Status | Notes |
|-----------|--------|-------|
| Docker Compose (local) | ✅ Full | All services running, database persists data |
| Backend microservices | ✅ Full | API Gateway, Wallet, Transaction all operational |
| PostgreSQL database | ✅ Full | Connections, queries, schema working |
| API routing | ✅ Full | Spring Cloud Gateway routes correctly |
| Direct API tests | ✅ Full | curl/Postman to `/api/*` endpoints work |
| Terraform IaC | ✅ Full | AWS resources provision and destroy cleanly |
| Kubernetes manifests | ✅ Full | Pods deploy, services discover each other |
| gRPC service calls | ✅ Full | Wallet Service ↔ Transaction Service |
| Ansible playbook | ✅ Fixed | Repaired and ready to use |
| Service-to-service auth | ✅ Works | gRPC + JWT tokens functional |

### Known Issues

| Issue | Problem | Solution |
|-------|---------|----------|
| **Frontend CORS Auth** | Browser signup/login fails | Update CORS `allowedOriginPatterns` in `api-gateway/application.yml` to explicit hostname:port instead of wildcard |
| **Kafka in K8s** | Not configured in cluster | ✅ Already handled: Wallet Service disables Kafka with `@ConditionalOnProperty` |
| **Frontend port 80** | Nginx (non-root) can't bind privileged port | ✅ Already fixed: Changed to port 8080 |
| **Ansible not installed** | `ansible-playbook` command not found | Install: `pip install ansible` |

### ✅ Safe Practices

**1. Local Development (.env files)**

```bash
# .env (in .gitignore)
AWS_ACCESS_KEY_ID=your_key_here
AWS_SECRET_ACCESS_KEY=your_secret_here
DB_PASSWORD=localpassword
JWT_SECRET=local_jwt_secret_key

# Use in docker-compose:
environment:
  - AWS_ACCESS_KEY_ID=${AWS_ACCESS_KEY_ID}
```

**2. Git Safe**

```bash
# .gitignore must include:
.env
.env.local
*.tfvars         # Don't commit terraform variables
kubeconfig*      # Don't commit cluster configs
~/.aws/          # Never commit AWS credentials

# Example .env.example (SAFE to commit)
AWS_ACCESS_KEY_ID=YOUR_KEY_HERE
AWS_SECRET_ACCESS_KEY=YOUR_SECRET_HERE
```

**3. Terraform State (Remote S3)**

```hcl
# backend.tf
terraform {
  backend "s3" {
    bucket         = "finstream-terraform-state"
    key            = "prod/terraform.tfstate"
    region         = "us-east-1"
    encrypt        = true           # ✅ Encrypted
    dynamodb_table = "terraform-locks"  # ✅ Locked
  }
}
```

**4. Kubernetes Secrets (Production)**

```bash
# Create sealed secrets (requires sealed-secrets controller)
kubectl create secret generic db-creds \
  --from-literal=username=admin \
  --from-literal=password=secure_pass \
  -o yaml | kubeseal -f - > db-secret-sealed.yaml

# Commit sealed-secret YAML, never the plaintext
git add k8s/base/db-secret-sealed.yaml
```

**5. GitHub/GitLab Secrets (CI/CD)**

```yaml
# .github/workflows/deploy.yml
env:
  AWS_ACCESS_KEY_ID: ${{ secrets.AWS_ACCESS_KEY_ID }}      # ✅ Stored in GitHub
  AWS_SECRET_ACCESS_KEY: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
  DB_PASSWORD: ${{ secrets.DB_PASSWORD }}
```

### Before Pushing to GitHub

**Checklist:**

```bash
# 1. Verify .gitignore is correct
cat .gitignore | grep -E "\.env|\.tfvars|kubeconfig"

# 2. Scan for accidental secrets
git diff --cached | grep -i "password\|secret\|key\|token"

# 3. Check uncommitted files (might contain secrets)
git status

# 4. If secrets were committed, scrub with BFG Repo-Cleaner
bfg --replace-text secrets.txt repo.git
```

### AWS EKS Deployment (Cloud)

```
┌──────────────────────────────────────────────────────────┐
│                    AWS Account                           │
├──────────────────────────────────────────────────────────┤
│                                                          │
│  VPC (10.0.0.0/16)                                       │
│  ├── Public Subnets (3x AZs)                             │
│  │   └── NLB LoadBalancer → Frontend:8080                │
│  │   └── NLB LoadBalancer → API Gateway:8080             │
│  │                                                       │
│  ├── Private Subnets (3x AZs)                            │
│  │   └── EKS Nodes (t3.small)                            │
│  │       ├── api-gateway-pod                             │
│  │       ├── wallet-service-pod                          │
│  │       ├── transaction-service-pod                     │
│  │       └── frontend-pod                                │
│  │                                                       │
│  └── RDS PostgreSQL (Multi-AZ)                           │
│      └── finstream database                              │
│                                                          │
│  ECR (Container Registry)                                │
│  ├── finstream-dev-api-gateway:v1                        │
│  ├── finstream-dev-wallet-service:v1                     │
│  ├── finstream-dev-transaction-service:v1                │
│  └── finstream-dev-frontend:v1                           │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

**Pros:**
- Auto-scaling
- High availability
- Managed by AWS
- Production-ready

---

## Infrastructure-as-Code

### Terraform Structure

```
infra/terraform/
├── main.tf              # VPC + EKS + RDS + ECR definitions
├── variables.tf         # Input variables (region, cluster version, etc.)
├── outputs.tf           # Output values (cluster name, RDS endpoint, ECR URLs)
├── terraform.tfvars     # ⚠️ SECRETS - in .gitignore!
├── .terraform.lock.hcl  # Lock file (dependency versions)
├── bootstrap/           # ONE-TIME SETUP
│   ├── main.tf         # Creates S3 bucket + DynamoDB table
│   ├── outputs.tf      # Prints bucket & table names
│   └── variables.tf
└── tfplan              # Cached plan file
```

### One-Time Bootstrap (S3 + DynamoDB)

First deployment only:

```bash
cd infra/terraform/bootstrap
terraform init
terraform apply  # Creates S3 + DynamoDB for state management
terraform output # Prints bucket name
```

Then configure main Terraform backend:

```hcl
# infra/terraform/main.tf
terraform {
  backend "s3" {
    bucket         = "finstream-terraform-state-xxxx"
    key            = "dev/terraform.tfstate"
    region         = "us-east-1"
    encrypt        = true
    dynamodb_table = "terraform-state-locks"
  }
}
```

### Deploying Infrastructure

```bash
cd infra/terraform

# 1. Initialize
terraform init

# 2. Review changes
terraform plan -out=tfplan

# 3. Apply
terraform apply tfplan

# 4. Get outputs
terraform output
# Prints: cluster_name, rds_endpoint, ecr_registries, etc.
```

### Destroying Infrastructure

```bash
cd infra/terraform
terraform destroy -auto-approve
```

⚠️ **Note**: This deletes everything (EKS, RDS, ECR) but keeps S3 + DynamoDB in bootstrap/

---

## Ansible Deployment

### What Ansible Playbook Does

```
ansible-playbook -i localhost, infra/ansible/deploy.yml
│
├─ Verify all service directories exist
├─ Get AWS account ID
├─ Login to ECR registry
├─ Build all Docker images
│  ├─ api-gateway
│  ├─ wallet-service
│  ├─ transaction-service
│  └─ frontend
├─ Push all images to ECR
├─ Check kubectl access
├─ Create K8s namespace
├─ Apply ConfigMaps & Secrets
├─ Deploy all services
└─ Wait for all pods ready (5 min timeout)
```

### Prerequisites

```bash
# 1. AWS CLI configured
aws sts get-caller-identity
# Should return your account info

# 2. Docker running
docker ps

# 3. kubectl configured
kubectl cluster-info
# Should connect to your EKS cluster

# 4. Ansible installed
pip install ansible
```

### Running the Playbook

```bash
# Full deployment
cd /d/code/java-app
ansible-playbook -i localhost, infra/ansible/deploy.yml

# Dry-run (show what will happen)
ansible-playbook -i localhost, infra/ansible/deploy.yml --check

# Verbose output
ansible-playbook -i localhost, infra/ansible/deploy.yml -vv
```

### Post-Deployment Check

```bash
# Verify all pods running
kubectl get pods -n finstream

# Watch pod startup
kubectl get pods -n finstream -w

# Get external IPs
kubectl get svc -n finstream

# View logs
kubectl logs -n finstream -l app=api-gateway --tail=50
```

---

## Troubleshooting

### Terraform Issues

**Problem**: `Error: error acquiring the lock`
```
Solution: DynamoDB lock is stale
- Check DynamoDB table in AWS console
- Delete the lock entry if it's old
- Or run: terraform force-unlock <LOCK_ID>
```

**Problem**: `Error: resource still has dependencies`
```
Solution: Dependencies not fully deleted
- Run: terraform apply -refresh-only
- Then: terraform destroy
```

**Problem**: `Error: Provider configuration missing`
```
Solution: Missing credentials
- Run: aws configure
- Or: export AWS_ACCESS_KEY_ID=xxx AWS_SECRET_ACCESS_KEY=yyy
```

### Kubernetes Issues

**Problem**: `ImagePullBackOff`
```
Solution: ECR image not found
- Verify image exists: aws ecr describe-images --repository-name finstream-dev-api-gateway
- Verify IAM role has ECR permissions
- Check EKS node IAM role
```

**Problem**: `CrashLoopBackOff`
```
Solution: Pod is crashing
- Check logs: kubectl logs -n finstream <pod-name>
- Check resources: kubectl describe pod -n finstream <pod-name>
- Look for exit codes and error messages
```

**Problem**: `Pending`
```
Solution: Pod waiting for resources/node
- Check node capacity: kubectl describe nodes
- Check pod requests: kubectl describe pod -n finstream <pod-name>
- May need to scale cluster or reduce pod resource requests
```

**Problem**: `Service has no endpoints`
```
Solution: Pod not running yet
- Wait longer: kubectl get svc -n finstream -w
- Or check pod status: kubectl get pods -n finstream
```

### Database Issues

**Problem**: `psql: could not translate host name to address`
```
Solution: RDS endpoint not reachable
- Check RDS security group allows EKS subnet CIDR
- Check RDS is in same VPC as EKS
```

**Problem**: `FATAL: password authentication failed`
```
Solution: Wrong credentials
- Check environment variables in deployment YAML
- Verify RDS master username/password
```

### Docker/Build Issues

**Problem**: `Dockerfile: no such file or directory`
```
Solution: Not in correct directory
- Verify you're in service directory before building
- Check Dockerfile exists: ls -la Dockerfile
```

**Problem**: `docker build permission denied`
```
Solution: Docker daemon not running or permission issue
- Check: docker ps
- On Linux: usermod -aG docker $USER && newgrp docker
```

---

## Checklist: Deploy to Production

- [ ] Secrets moved to AWS Secrets Manager or GitHub Secrets
- [ ] .gitignore properly configured (no secrets committed)
- [ ] Terraform backend (S3 + DynamoDB) created
- [ ] AWS credentials configured (`aws configure`)
- [ ] kubectl pointing to production cluster
- [ ] Ansible installed (`pip install ansible`)
- [ ] CORS origins updated in api-gateway application.yml
- [ ] Terraform plan reviewed (`terraform plan`)
- [ ] Terraform applied successfully
- [ ] Ansible playbook executed (`ansible-playbook ...`)
- [ ] All pods running (`kubectl get pods -n finstream`)
- [ ] External LB endpoints accessible
- [ ] Signup/login tested from browser
- [ ] API tested with curl
- [ ] Database persistence verified
- [ ] Logs reviewed for errors

---

## Resources

- [AWS EKS Best Practices](https://aws.github.io/aws-eks-best-practices/)
- [Kubernetes Security](https://kubernetes.io/docs/concepts/security/)
- [Terraform AWS Provider](https://registry.terraform.io/providers/hashicorp/aws/latest/docs)
- [Ansible Documentation](https://docs.ansible.com/)
- [Spring Boot Security](https://spring.io/guides/gs/securing-web/)
- [Vue 3 Security](https://vuejs.org/guide/best-practices/security.html)