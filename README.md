# Loan Service (ms-lending-app-loan-service)

Manages the loan lifecycle: creation, repayment processing, billing cycles, installments, overdue sweep jobs, and state management.

## Tech Stack

| Component        | Technology                              |
|------------------|-----------------------------------------|
| Framework        | Spring Boot 3.4.4 / Spring WebFlux      |
| Language         | Java 21                                 |
| Database         | PostgreSQL (R2DBC — reactive)           |
| Migrations       | Flyway (runs over JDBC at startup)      |
| Event Broker     | Apache Kafka (produces `lending.loan.events`) |
| Security         | API Key (`X-API-KEY` header)            |
| Testing          | JUnit 5 + Mockito + StepVerifier        |

## Prerequisites

- Java 21+
- Maven 3.9+
- PostgreSQL 15+
- Apache Kafka 3.x+
- Customer Service running on `localhost:8082`
- Create database: `CREATE DATABASE lending_loan_db;`

## Getting Started

```bash
cd ms-lending-app-loan-service

# Build
mvn clean compile

# Run
mvn spring-boot:run

# Run tests
mvn clean test
```

The service starts on **port 8083** and Flyway auto-creates all tables on first startup.

## Configuration

| Property                           | Default                                            |
|------------------------------------|----------------------------------------------------|
| `server.port`                      | `8083`                                             |
| `spring.r2dbc.url`                 | `r2dbc:postgresql://localhost:5432/lending_loan_db` |
| `app.security.api-key`             | `loan-service-api-key-2024`                        |
| `spring.kafka.bootstrap-servers`   | `localhost:9092`                                   |
| `app.service.customer-url`         | `http://localhost:8082`                             |
| `app.sweep.cron`                   | `0 0 * * * *` (hourly)                             |

## Database Schema

Flyway migrations create:

- **billing_cycles** — consolidated billing with due day per customer
- **loans** — loan records with state and idempotency key
- **loan_installments** — individual installments for installment-type loans
- **loan_repayments** — repayment transaction records
- **loan_fees** — fees charged on loans (service, daily, late)

## Loan Creation Flow

Loan creation is a simple reactive pipeline:

```
1. Check idempotency key — if loan already exists, return it immediately
2. Persist new loan (state = OPEN)
3. Publish LOAN_CREATED event to Kafka
4. If INSTALLMENT type, generate installment schedule
```

**Idempotency:** Each loan request requires a unique `Idempotency-Key` header. Duplicate keys return the existing loan without creating a new one.

## API Endpoints

All endpoints require header: `X-API-KEY: loan-service-api-key-2024`

| Method | Endpoint                                | Description                      |
|--------|-----------------------------------------|----------------------------------|
| `POST` | `/api/v1/loans`                         | Create loan (idempotent)         |
| `GET`  | `/api/v1/loans/{loanId}`                | Get loan with installments       |
| `GET`  | `/api/v1/loans/customer/{customerId}`   | Get all loans for customer       |
| `POST` | `/api/v1/loans/repayments`              | Process repayment                |
| `PUT`  | `/api/v1/loans/{loanId}/cancel`         | Cancel loan (OPEN only)          |

## Loan State Machine

```
OPEN ──repay──▶ CLOSED
  │
  ├──cancel──▶ CANCELLED
  │
  ├──overdue sweep──▶ OVERDUE
  │
  └──write off──▶ WRITTEN_OFF
```

## Kafka Events Published

Topic: `lending.loan.events` (6 partitions, key = `customerId`)

| Event Type          | Trigger                          |
|---------------------|----------------------------------|
| `LOAN_CREATED`      | Loan successfully created        |
| `REPAYMENT_RECEIVED`| Partial repayment processed      |
| `LOAN_CLOSED`       | Loan fully repaid                |
| `LOAN_CANCELLED`    | Loan cancelled                   |
| `OVERDUE_NOTICE`    | Sweep job detects overdue loans  |

## Overdue Sweep Job

A `@Scheduled` component (`OverdueSweepJob`) runs on the `app.sweep.cron` schedule (default: hourly).

**What it does:**
1. Finds all **OPEN** loans with `dueDate` before today → marks them **OVERDUE** and publishes an `OVERDUE_NOTICE` Kafka event per loan.
2. Finds all **PENDING** installments with `dueDate` before today → marks them **OVERDUE**.

## Billing Cycle

The `billing_cycles` table stores per-customer consolidated billing preferences (`consolidatedDueDay`, `isConsolidated`). A `billingCycleId` is optionally associated with a loan at creation time. This is a reference field — consolidated billing logic is handled externally by the billing service.

## Example Request — Create Loan

```bash
curl -X POST http://localhost:8083/api/v1/loans \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: loan-service-api-key-2024" \
  -H "Idempotency-Key: LOAN-REQ-001" \
  -d '{
    "customerId": "d1e2f3a4-b5c6-7890-def1-234567890abc",
    "productId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "principalAmount": 10000.00,
    "loanType": "LUMP_SUM",
    "tenureValue": 30,
    "tenureType": "DAYS"
  }'
```

## Project Structure

```
src/main/java/com/glo/lending/loan/
├── LoanServiceApplication.java
├── components/
│   ├── LoanEventPublisher.java            # Kafka event publisher
│   └── OverdueSweepJob.java               # Scheduled job: marks overdue loans/installments, publishes OVERDUE_NOTICE
├── config/
│   ├── KafkaProducerConfig.java           # 6 partitions, idempotent, acks=all
│   └── SecurityConfig.java
├── controller/
│   └── LoanController.java
├── exception/
│   ├── GlobalExceptionHandler.java
│   └── LoanNotFoundException.java
├── model/
│   ├── dto/                               # CreateLoanRequest, RepaymentRequest, responses
│   └── enums/                             # LoanState, LoanType, InstallmentState
├── repository/
│   ├── entities/                          # Loan, LoanInstallment, LoanRepayment, LoanFee, BillingCycle
│   └── repo/                              # Reactive repositories
├── service/
│   ├── LoanService.java                   # Interface
│   └── serviceImpl/
│       └── LoanServiceImpl.java           # Implementation
└── utils/
    └── LoanMapper.java
```
