# FinDash Platform

## Overview

FinDash is a simulated digital wallet and payment platform. It lets users:
- Register and authenticate (JWT + BCrypt)
- Manage wallets and view real-time balances
- Send transfers between users
- Review transaction history and basic analytics

The system showcases a microservices architecture with Kafka for events, gRPC for synchronous calls, and a gateway with circuit breakers and rate limiting. Security, validation, and observability are included.

## Prerequisites

> [!IMPORTANT]
> **For Docker (recommended):**
> - Docker Desktop or Docker Engine (20.10+)
> - Docker Compose (2.0+)
> **For local development without Docker:**
> - Java 21+ (JDK)
> - Maven 3.9+
> - Node.js 18+
> - Docker Compose (still needed for Postgres and Kafka)

## Architecture

### Services Overview
- **Frontend** (Vue 3 + Vite) - User interface served by Nginx on port 3000
- **API Gateway** (Spring Cloud Gateway + Resilience4J) - Routes requests, handles CORS, circuit breaking on port 8080
- **Wallet Service** (Spring Boot) - User accounts, wallets, balances on port 8081, gRPC server is on port 9091
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
┌─────────────────────┐                    ┌─────────────────────┐
│  Wallet Service     │◄───── gRPC ────────│ Transaction Service │
│  :8081 (REST)       │      validate      │  :8082 (REST)       │
│  :9091 (gRPC)       │      user/wallet   │                     │
│                     │                    │                     │
│  - User accounts    │                    │  - Transfer logic   │
│  - Wallet CRUD      │                    │  - Kafka producer   │
│  - Kafka consumer   │                    └──────────┬──────────┘
└──────┬──────────────┘                               │
       │                                              │ Publishes
       │ Reads                                        ▼
       │                                    ┌──────────────────┐
       │                                    │  Kafka :9092     │
       │                                    │  Topic:          │
       │                                    │  "transactions"  │
       │                                    └──────────────────┘
       │ Consumes                                     │
       └──────────────────────────────────────────────┘
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

3. **Transaction → Wallet** (gRPC - synchronous)
   - Before creating a transfer, Transaction Service validates the sender and receiver
   - Validation happens via the Wallet Service gRPC endpoint at `wallet-service:9091`

4. **Transaction → Kafka** (Event streaming - async)
   - After validation, Transaction Service publishes an event to the Kafka topic
   - The event includes sender, receiver, and amount

5. **Kafka → Wallet** (Consumer - async)
   - Wallet Service listens on the `transactions` topic
   - It consumes events and updates balances in the database

6. **Services → Database** (JDBC with HikariCP pooling)
   - Both services connect to the same PostgreSQL database
   - Wallet owns `users` and `wallets` tables
   - Transaction owns the `transactions` table

## Tech Stack
- Backend: Java 21, Spring Boot 3, Spring Cloud Gateway, Resilience4J, Micrometer
- Messaging: Apache Kafka
- Data: PostgreSQL, HikariCP
- Security: JWT (Bearer), BCrypt, Jakarta Validation, rate limiting (Bucket4j)
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
- `wallet-service/src/main/java/com/finstream/wallet/grpc/WalletGrpcService.java` - gRPC server for validation
- `wallet-service/src/main/java/com/finstream/wallet/kafka/TransactionConsumer.java` - Kafka listener for balance updates
- `wallet-service/src/main/java/com/finstream/wallet/security/JwtUtil.java` - JWT token generation/validation
- `wallet-service/src/main/resources/application.yml` - DB, Kafka, JWT config

### Transaction Service
- `transaction-service/src/main/java/com/finstream/transaction/controller/TransactionController.java` - Transfer endpoints
- `transaction-service/src/main/java/com/finstream/transaction/grpc/WalletGrpcClient.java` - gRPC client to validate wallets
- `transaction-service/src/main/java/com/finstream/transaction/kafka/TransactionProducer.java` - Kafka publisher
- `transaction-service/src/main/resources/application.yml` - DB, Kafka, gRPC config

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
```powershell
docker-compose up -d --build
```
Services:
- Frontend: http://localhost:3000
- API Gateway: http://localhost:8080

## Run Locally (without Docker)

> [!NOTE]
> Prereqs: Java 21+, Maven, Node 18+

```powershell
# Backend (from repo root)
mvn clean package -DskipTests

# Terminal 1: Postgres + Kafka (run via Docker Compose even for local dev)
docker-compose up -d postgres zookeeper kafka

# Terminal 2: Wallet Service
mvn -pl wallet-service spring-boot:run

# Terminal 3: Transaction Service
mvn -pl transaction-service spring-boot:run

# Terminal 4: API Gateway
mvn -pl api-gateway spring-boot:run

# Frontend (hot reload)
cd frontend
npm install
npm run dev
```

## API Endpoints (via Gateway)
- Wallet
  - POST /api/wallet/users — Register (returns `token`)
  - POST /api/wallet/login — Login (returns `token`)
  - GET  /api/wallet/users — List users (public)
  - GET  /api/wallet/{userId}/balance — Get balance (auth required)
- Transaction
  - GET  /api/transaction/history/{userId} — List transactions (auth)
  - POST /api/transaction/transfer — Create transfer (auth)

Auth header after login/register:
```
Authorization: Bearer <token>
```

## Quick Test (Windows-safe)
```powershell
$u1 = @{ fullName = "QA One";  email = "qa.one@example.com";  password = "TestPassword!234" } | ConvertTo-Json
$u2 = @{ fullName = "QA Two";  email = "qa.two@example.com";  password = "TestPassword!234" } | ConvertTo-Json
$r1 = Invoke-RestMethod -Uri "http://localhost:8080/api/wallet/users" -Method POST -ContentType "application/json" -Body $u1
$r2 = Invoke-RestMethod -Uri "http://localhost:8080/api/wallet/users" -Method POST -ContentType "application/json" -Body $u2
$login = @{ email = "qa.one@example.com"; password = "TestPassword!234" } | ConvertTo-Json
$auth = Invoke-RestMethod -Uri "http://localhost:8080/api/wallet/login" -Method POST -ContentType "application/json" -Body $login
$token = $auth.token
$xfer = @{ senderEmail = "qa.one@example.com"; receiverEmail = "qa.two@example.com"; amount = 10.5 } | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8080/api/transaction/transfer" -Method POST -ContentType "application/json" -Headers @{ Authorization = "Bearer $token" } -Body $xfer
```

## Troubleshooting
- Gateway root '/' returns 404 — use `/api/...` routes.
- Windows quoting: use PowerShell with `ConvertTo-Json` to avoid 400 parse errors.
- First run: wait a few seconds for Postgres/Kafka to warm up.
- 401 after restart: ensure the same `JWT_SECRET` remains in `.env`.
- 429 on login: rate limiting — wait 1 minute and retry.

## Production Concerns
- CORS: Gateway uses `allowedOriginPatterns: "*"` with credentials.
- TLS/Proxy: terminate TLS in a reverse proxy or load balancer in front of gateway.
- Observability: Actuator health and Micrometer metrics are included.
- Resilience: Circuit breakers and timeouts are configured.
