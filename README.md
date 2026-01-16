# FinDash Platform

## Overview

FinDash is a simulated digital wallet and payment platform that lets users:
- Sign up and log in securely (JWT + HttpOnly cookies)
- Check wallet balances and transaction history
- Send money to other users
- Manage contacts and view analytics

Built with Vue 3 frontend, Spring Boot microservices, PostgreSQL, and Kafka for messaging.

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
- Frontend: http://localhost:3000
- API Gateway: http://localhost:8080

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
Invoke-RestMethod -Uri "http://localhost:8080/api/wallet/login" -Method POST -ContentType "application/json" -Body $login -WebSession $session

# Send $5 to Bob
$xfer = @{ senderEmail = "alice@test.com"; receiverEmail = "bob@test.com"; amount = 5 } | ConvertTo-Json
Invoke-RestMethod -Uri "http://localhost:8080/api/transaction/transfer" -Method POST -ContentType "application/json" -Body $xfer -WebSession $session
```

## Security

**Authentication:** JWT tokens stored in secure HttpOnly cookies. Browser automatically sends with each request. Never stored in localStorage/sessionStorage.

**Password:** BCrypt hashing with 10+ salt rounds.

**Rate Limiting:** Max 5 login attempts per minute to prevent brute force.

**CORS:** Configured for localhost. Update for production domains only.

**Database:** All queries are parameterized (no SQL injection).

**Production Checklist:**
- Set `Secure` flag on cookies (requires HTTPS)
- Use 256+ bit JWT secret in secure vault
- Enable TLS 1.3+ on all connections
- Update allowed CORS origins to production domains
- Review rate limit thresholds

## Codebase Improvements

- ✅ Centralized auth state via shared `useAuth()` composable
- ✅ Single auth check on app startup (no race conditions)
- ✅ Removed duplicate functions (`formatRelativeTime`, `loadUser`, `handleLogout`, etc.)
- ✅ Removed sessionStorage usage (only HttpOnly cookies for auth)
- ✅ Contact persistence with localStorage (per-user keys)
- ✅ Contact search filters out already-added contacts
- ✅ Dark mode initialization prevents UI flashing
- ✅ All components use proper TypeScript types
