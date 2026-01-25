# FinDash Platform

## Overview

FinDash is a simulated digital wallet and payment platform that lets users:
- Sign up and log in securely (JWT + HttpOnly cookies)
- Check wallet balances and transaction history
- Send money to other users
- Manage contacts and view analytics

Built with Vue 3 frontend, Spring Boot microservices, PostgreSQL, gRPC, and Kafka for messaging.

## Prerequisites

> [!IMPORTANT]
> **For Docker (recommended):**
> - Docker Desktop or Docker Engine (20.10+)
> - Docker Compose (2.0+)
>
> **For local development without Docker:**
> - Java 21+ (JDK)
> - Maven 3.9+
> - Node.js 18+
> - Docker Compose (still needed for Postgres and Kafka)

## Architecture

### Services Overview
- **Frontend** (Vue 3 + Vite) - User interface served by Nginx on port 3000
- **API Gateway** (Spring Cloud Gateway + Resilience4J) - Routes requests, handles CORS, circuit breaking on port 8080
- **Wallet Service** (Spring Boot) - User accounts, wallets, balances on port 8081 (REST) and port 9091 (gRPC)
- **Transaction Service** (Spring Boot) - Payment processing, transfers on port 8082
- **Kafka + Zookeeper** - Event streaming for async transaction processing
- **PostgreSQL** - Single database `findash` with user, wallet, and transaction tables

### Visual Diagram (Communication Flow)

```
┌──────────────────────────────────────────────────────────────────────┐
│                           User Browser                               │
└───────────────────────────────┬──────────────────────────────────────┘
                                │ HTTP
                                ▼
                    ┌───────────────────────┐
                    │   Frontend (Vue 3)    │
                    │   Nginx :3000         │
                    └───────────┬───────────┘
                                │ HTTP REST
                                ▼
                    ┌───────────────────────┐
                    │   API Gateway :8080   │
                    │  - Routes requests    │
                    │  - Circuit breakers   │
                    │  - CORS, Rate limit   │
                    └─────┬─────────────┬───┘
                          │             │ HTTP Routes
          ┌───────────────┘             └───────────────┐
          │ /api/wallet/**                  /api/transaction/**
          ▼                                              ▼
┌─────────────────────────────────┐       ┌──────────────────────────────┐
│  Wallet Service                 │       │  Transaction Service         │
│  :8081 (REST) :9091 (gRPC)      │◄──────│  :8082 (REST)                │
│                                 │ gRPC  │                              │
│  - User accounts                │ :9091 │  - Transfer logic            │
│  - Wallet CRUD                  │       │  - gRPC Client               │
│  - gRPC Server                  │       │  - Saves tx to DB            │
│    (checkSufficientBalance)     │       └──────────────┬───────────────┘
│  - Kafka consumer               │                      │ Publishes
└──────┬──────────────────────────┘                     ▼
       │                                     ┌──────────────────────┐
       │ Reads                               │  Kafka :9092         │
       │                                     │  Topic:              │
       │                                     │  "transactions"      │
       │                                     └──────────────────────┘
       │ Consumes                                      │
       └───────────────────────────────────────────────┘
       │
       │ Updates balance
       ▼
┌────────────────────────────────────────────────────┐
│           PostgreSQL :5432                         │
│           Database: findash                        │
│  ┌──────────┐  ┌──────────┐  ┌──────────────────┐  │
│  │  users   │  │  wallets │  │  transactions    │  │
│  └──────────┘  └──────────┘  └──────────────────┘  │
└────────────────────────────────────────────────────┘
```

### How Services Communicate

1. **Frontend → Gateway** (HTTP/REST)
   - Users register and log in via the Vue UI
   - All API calls go through the gateway at `http://localhost:8080`

2. **Gateway → Services** (HTTP/REST with circuit breakers)
   - Gateway routes `/api/wallet/**` to Wallet Service
   - Gateway routes `/api/transaction/**` to Transaction Service
   - If a service is unavailable, the gateway returns a 503 fallback

3. **Transaction → Wallet** (gRPC - synchronous balance validation)
   - Before processing a transfer, Transaction Service calls Wallet Service via gRPC
   - The `checkSufficientBalance` RPC verifies sender has enough funds
   - If balance is insufficient, transaction is rejected immediately with 400 Bad Request
   - gRPC provides fast, type-safe validation before any database writes

4. **Transaction → Kafka** (Event streaming - async)
   - After validation, Transaction Service saves the transaction to DB and publishes an event to Kafka
   - The event includes sender ID, receiver ID, and amount

5. **Kafka → Wallet** (Consumer - async)
   - Wallet Service listens on the `transactions` topic
   - It consumes events and updates balances in the database

6. **Services → Database** (JDBC with HikariCP pooling)
   - Both services connect to the same PostgreSQL database
   - Wallet owns `users` and `wallets` tables
   - Transaction owns the `transactions` table

## Tech Stack
- Backend: Java 21, Spring Boot 3, Spring Cloud Gateway, Resilience4J, Micrometer
- RPC: gRPC 1.63.0, Protocol Buffers 3
- Messaging: Apache Kafka
- Data: PostgreSQL, HikariCP
- Security: JWT (HttpOnly Cookies), BCrypt, Jakarta Validation, rate limiting (Bucket4j), CORS with credentials
- Frontend: Vue 3, Vite, Tailwind, Axios
- Containers: Docker, Docker Compose

## Key Files & What They Do

### API Gateway
- `api-gateway/src/main/java/com/finstream/gateway/ApiGatewayApplication.java` - Gateway entry point
- `api-gateway/src/main/resources/application.yml` - Route definitions, circuit breaker config, CORS
- `api-gateway/src/main/java/com/finstream/gateway/controller/FallbackController.java` - Fallback responses when services down

### Wallet Service
- `wallet-service/src/main/java/com/finstream/wallet/controller/UserController.java` - Registration, login, user management
- `wallet-service/src/main/java/com/finstream/wallet/controller/WalletController.java` - Wallet balance operations
- `wallet-service/src/main/java/com/finstream/wallet/grpc/GrpcWalletService.java` - gRPC server for balance validation
- `wallet-service/src/main/java/com/finstream/wallet/kafka/TransactionEventConsumer.java` - Kafka listener for balance updates
- `wallet-service/src/main/java/com/finstream/wallet/security/JwtUtil.java` - JWT token generation/validation
- `wallet-service/src/main/resources/application.yml` - DB, Kafka, JWT, gRPC config

### Transaction Service
- `transaction-service/src/main/java/com/finstream/transaction/controller/TransactionController.java` - Transfer endpoints
- `transaction-service/src/main/java/com/finstream/transaction/service/TransactionOrchestrator.java` - Transaction processing, gRPC validation, and Kafka publishing
- `transaction-service/src/main/java/com/finstream/transaction/grpc/WalletGrpcClient.java` - gRPC client for wallet service calls
- `transaction-service/src/main/resources/application.yml` - DB, Kafka, and gRPC client config

### Common (Shared)
- `common/src/main/proto/service.proto` - Protocol Buffers definition for gRPC services

### Frontend
- `frontend/src/views/Login.vue` - Login/register UI
- `frontend/src/views/Dashboard.vue` - User dashboard with balance and transfers
- `frontend/src/main.ts` - Axios interceptors for JWT auth
- `frontend/nginx.conf` - Nginx config for SPA routing

### Infrastructure
- `docker-compose.yml` - All services orchestration with health checks and resource limits
- `docker/postgres/init.sql` - Database schema creation

## Configuration (.env)
Copy the template and set values:

```powershell
Copy-Item .env.example .env
# Edit .env and set:
# DB_USERNAME=findash_user
# DB_PASSWORD=ChangeMe_1234!
# JWT_SECRET=long-random-secret
# WALLET_SEED_DEMO=false
```
> [!NOTE]
> For production, use a very long `JWT_SECRET` and managed secrets.

## Run with Docker (recommended)

**First time or after errors:**
```powershell
docker-compose up -d --build
```

**If you encounter Kafka/Zookeeper errors (NodeExistsException):**
```powershell
docker-compose down -v
docker-compose up -d
```

- Frontend: http://localhost:3000
- API Gateway: http://localhost:8080

> [!TIP]
> The `-v` flag removes volumes including Zookeeper state, which fixes session conflicts.

## Deployment Methods

### 📦 Three Deployment Approaches Supported

This project supports **three different deployment methods** with environment-specific configurations:

#### 1. **Local Docker Compose** (Development)
- Perfect for local development and testing
- All services in containers, single command to start
- **Command**: `docker-compose up -d`
- **Access**: http://localhost:3000
- **Database**: PostgreSQL container
- **See**: [Docker-Compose Guide](#locally)

#### 2. **Kubernetes on AWS EKS** (Production)
- Full production-grade Kubernetes deployment
- Auto-scaling, rolling updates, load balancing
- Uses AWS RDS for managed PostgreSQL
- **Command**: `bash k8s/deploy.sh`
- **Setup**: Edit `k8s/.env` with your AWS info
- **See**: [k8s/README.md](k8s/README.md)

#### 3. **Terraform Infrastructure as Code** (Full Stack)
- Provision entire AWS infrastructure automatically
- Creates VPC, EKS cluster, RDS database, and ECR registries
- **Command**: `terraform apply` (in `infra/terraform/`)
- **Setup**: Update `terraform.tfvars` with your values
- **See**: [Terraform Configuration](infra/terraform/)

### 🔄 How All Methods Work Together

All three methods use the **same application code** with environment-specific configuration:

| Component | Docker-Compose | Kubernetes | Terraform |
|-----------|---|---|---|
| **Database** | PostgreSQL container `postgres:5432` | RDS endpoint via `${DB_HOST}` | RDS created by Terraform |
| **Credentials** | docker-compose.yml defaults | K8s Secrets | terraform.tfvars (⚠️ confidential) |
| **Frontend Port** | 3000 (maps to 80) | 8080 (LoadBalancer) | 8080 via EKS |

### Locally
```powershell
# Build all services
mvn clean package -DskipTests

# Terminal 1: Start database and messaging
docker-compose up -d postgres zookeeper kafka

# Terminal 2: Wallet Service
mvn -pl wallet-service spring-boot:run

# Terminal 3: Transaction Service
mvn -pl transaction-service spring-boot:run

# Terminal 4: API Gateway
mvn -pl api-gateway spring-boot:run

# Terminal 5: Frontend (with hot reload)
cd frontend
npm install
npm run dev
```

## API Endpoints

All requests go through the gateway at `http://localhost:8080`. Authentication is automatic via HttpOnly cookie.

### Wallet Service
- `POST /api/wallet/users` — Register new user
- `POST /api/wallet/login` — Log in (sets secure cookie)
- `POST /api/wallet/logout` — Log out
- `GET /api/wallet/users` — List all users
- `GET /api/wallet/{userId}/balance` — Get balance (requires auth)

### Transaction Service
- `GET /api/transaction/history/{userId}` — View transactions (requires auth)
- `POST /api/transaction/transfer` — Send money to another user (requires auth)

## Quick Test

```powershell
# Create two users
$u1 = @{ fullName = "Alice"; email = "alice@test.com"; password = "TestPass!123" } | ConvertTo-Json
$u2 = @{ fullName = "Bob"; email = "bob@test.com"; password = "TestPass!123" } | ConvertTo-Json

$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
Invoke-RestMethod -Uri "http://localhost:8080/api/wallet/users" -Method POST -ContentType "application/json" -Body $u1 -WebSession $session
Invoke-RestMethod -Uri "http://localhost:8080/api/wallet/users" -Method POST -ContentType "application/json" -Body $u2 -WebSession $session

# Log in as Alice
$login = @{ email = "alice@test.com"; password = "TestPass!123" } | ConvertTo-Json
$aliceLogin = Invoke-RestMethod -Uri "http://localhost:8080/api/wallet/login" -Method POST -ContentType "application/json" -Body $login -WebSession $session

# Get Bob's user ID
$users = Invoke-RestMethod -Uri "http://localhost:8080/api/wallet/users" -Method GET -WebSession $session
$bobId = ($users | Where-Object { $_.email -eq "bob@test.com" }).id

# Send $5 to Bob (need sender and receiver UUIDs)
$xfer = @{ senderId = $aliceLogin.id; receiverId = $bobId; amount = 5 } | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8080/api/transaction/transfer" -Method POST -ContentType "application/json" -Body $xfer -WebSession $session
```

## Security

**Authentication:** JWT tokens stored in secure HttpOnly cookies. Browser automatically sends with each request. Never stored in localStorage/sessionStorage.

**Password:** BCrypt hashing with 10+ salt rounds.

**Rate Limiting:** Max 5 login attempts per minute to prevent brute force.

**CORS:** Configured for localhost. Update for production domains only.

**Database:** All queries are parameterized (no SQL injection).

**gRPC:** Service-to-service communication using gRPC for synchronous balance validation. Currently configured with plaintext for local development.

**Production Checklist:**
- Set `Secure` flag on cookies (requires HTTPS)
- Use 256+ bit JWT secret in secure vault
- Enable TLS 1.3+ on all connections
- Configure TLS for gRPC (currently using plaintext for local dev)
- Update allowed CORS origins to production domains
- Review rate limit thresholds
- Implement gRPC authentication/authorization for service-to-service calls

## Codebase Improvements

- ✅ Centralized auth state via shared `useAuth()` composable
- ✅ Single auth check on app startup (no race conditions)
- ✅ Removed duplicate functions (`formatRelativeTime`, `loadUser`, `handleLogout`, etc.)
- ✅ Removed sessionStorage usage (only HttpOnly cookies for auth)
- ✅ Contact persistence with localStorage (per-user keys)
- ✅ Contact search filters out already-added contacts
- ✅ Dark mode initialization prevents UI flashing
- ✅ All components use proper TypeScript types
- ✅ gRPC integration for synchronous balance validation
- ✅ Dual-path architecture: gRPC for validation, Kafka for updates

## Transaction Flow with gRPC

When a user initiates a money transfer:

1. **User submits transfer** via Vue frontend → API Gateway → Transaction Service
2. **gRPC validation** (synchronous): Transaction Service calls Wallet Service via gRPC to verify sender has sufficient balance
   - If insufficient: Transaction rejected immediately with 400 Bad Request
   - If sufficient: Continue to step 3
3. **Save transaction**: Transaction Service persists transaction record with status `COMPLETED`
4. **Publish event** (asynchronous): Transaction Service publishes event to Kafka topic `transactions`
5. **Balance update** (asynchronous): Wallet Service consumes Kafka event and updates both sender and receiver balances

This dual-path ensures:
- **Fast validation**: gRPC provides immediate feedback (< 100ms typically)
- **Data consistency**: Kafka ensures eventual consistency for balance updates
- **Fault tolerance**: If Kafka is down, transaction is still validated and saved; balance update happens when Kafka recovers
