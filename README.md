# Digital Banking & Fraud Detection System

A **microservices-based digital banking platform** built with Java and Spring Boot that supports account management, account-to-account transfers, external deposits through Razorpay, real-time fraud detection, OTP-based transaction verification, and event-driven notifications.

The system uses **Apache Kafka for asynchronous communication, Redis for real-time fraud detection and OTP management, and Spring Cloud OpenFeign for synchronous inter-service communication**.

It implements SAGA-style transaction orchestration with compensating actions to handle failures across distributed services.

---

## 🚀 Key Features

- 🏦 **Microservices Architecture** — Independent services with dedicated responsibilities and databases.
- 👤 **Account Management** — Account creation, account lookup, balance management, and account blocking.
- 💸 **Internal Fund Transfers** — Transfer funds between existing Avionyx accounts.
- 💳 **External Deposits** — Add money to an Avionyx account using Razorpay Checkout.
- 🔄 **SAGA-Style Compensation** — Reverse previously completed debit operations when a transfer fails.
- 🛡️ **Real-Time Fraud Detection** — Redis-backed velocity, unusual amount, and balance percentage checks.
- 🔐 **OTP-Based Verification** — Require additional verification for suspicious transactions.
- 🚫 **Account Blocking** — Block accounts following configured suspicious activity rules.
- 📡 **Event-Driven Communication** — Kafka-based asynchronous communication between microservices.
- 💰 **Payment Refund Handling** — Refund captured external payments through Razorpay when required.
- 📧 **Event-Driven Notifications** — Transaction, payment, OTP, and refund notifications.
- ⚡ **API Rate Limiting** — Redis-backed request throttling through API Gateway.
- 🐳 **Containerized Infrastructure** — Docker-based deployment of supporting services.

---

## 🏗️ System Architecture

Avionyx follows a microservices architecture in which each service handles a specific business capability.

The API Gateway acts as the single entry point for client requests. Services communicate synchronously through OpenFeign when an immediate response is required and asynchronously through Kafka when an event-driven workflow is more appropriate.

### High-Level Architecture

```text
                         ┌──────────────────┐
                         │       User       │
                         └────────┬─────────┘
                                  │
                                  ▼
                         ┌──────────────────┐
                         │   API Gateway    │
                         │  Routing / Rate  │
                         │     Limiting     │
                         └────────┬─────────┘
                                  │
                  ┌───────────────┼────────────────┐
                  │               │                │
                  ▼               ▼                ▼
          ┌─────────────┐ ┌──────────────┐ ┌──────────────┐
          │   Account   │ │ Transaction  │ │   Payment    │
          │   Service   │ │   Service    │ │   Service    │
          └──────┬──────┘ └──────┬───────┘ └──────┬───────┘
                 │               │                │
                 ▼               │                ▼
          ┌─────────────┐        │          ┌──────────────┐
          │ Account DB  │        │          │   Razorpay   │
          └─────────────┘        │          │  Integration │
                                 │          └──────┬───────┘
                                 │                 │
                                 ▼                 ▼
                         ┌────────────────────────────┐
                         │          Kafka             │
                         │      Event Broker          │
                         └──────────────┬─────────────┘
                                        │
                     ┌──────────────────┼─────────────────┐
                     │                  │                 │
                     ▼                  ▼                 ▼
              ┌─────────────┐  ┌────────────────┐ ┌──────────────┐
              │    Fraud    │  │  Notification  │ │   Account    │
              │  Detection  │  │    Service     │ │   Service    │
              │   Service   │  │                │ │   Consumer   │
              └──────┬──────┘  └────────────────┘ └──────────────┘
                     │
                     ▼
                ┌─────────┐
                │  Redis  │
                │ Velocity│
                │ Amount  │
                │   OTP   │
                └─────────┘
```

---

## 🧩 Microservices

| Service | Responsibility |
|---|---|
| **API Gateway** | Central entry point, request routing, and Redis-backed rate limiting. |
| **Account Service** | Account creation, account lookup, balance operations, and account blocking. |
| **Transaction Service** | Internal transfers, transaction lifecycle management, OTP verification, and compensation. |
| **Fraud Detection Service** | Real-time transaction evaluation using Redis-backed fraud rules. |
| **Payment Service** | Razorpay order creation, payment processing, webhook handling, and external payment refunds. |
| **Notification Service** | Consumes Kafka events and sends relevant user notifications. |

### 1. API Gateway

Provides a unified entry point for frontend applications and routes requests to the appropriate microservice.

Responsibilities:

- Route account requests to Account Service.
- Route transaction requests to Transaction Service.
- Route payment requests to Payment Service.
- Apply request rate limiting using Redis.
- Handle cross-origin requests (CORS) from the frontend.

### 2. Account Service

Responsible for managing Avionyx accounts and their balances.

Responsibilities:

- Create new bank accounts.
- Retrieve account information.
- Check account balances.
- Credit and debit account balances.
- Block accounts when required.
- Consume transaction and fraud-related Kafka events.

The service maintains its own PostgreSQL database.

### 3. Transaction Service

Handles internal account-to-account transfers and coordinates their transaction lifecycle.

Responsibilities:

- Validate transfer requests.
- Debit the sender through Account Service using OpenFeign.
- Create and persist transaction records.
- Publish transaction initiation events.
- Handle fraud detection results.
- Generate and verify OTPs.
- Complete successful transactions.
- Compensate failed transactions by refunding the sender.
- Publish transaction lifecycle events.

### 4. Fraud Detection Service

Evaluates initiated transactions using configurable fraud detection rules.

Responsibilities:

- Consume transaction initiation events from Kafka.
- Retrieve relevant account information.
- Evaluate transaction velocity.
- Detect unusual transaction amounts.
- Check transaction amount against available balance thresholds.
- Store and retrieve fraud-related counters and baselines using Redis.
- Publish clean or suspicious transaction results.

### 5. Payment Service

Integrates Razorpay to support external deposits into Avionyx accounts.

Responsibilities:

- Create Razorpay payment orders.
- Store internal payment records.
- Handle Razorpay payment webhooks.
- Verify payment authenticity.
- Initiate refunds for rejected or fraudulent captured payments.
- Publish payment lifecycle events through Kafka.

The Payment Service maintains its own PostgreSQL database.

### 6. Notification Service

Consumes Kafka events and generates notifications for important banking activities.

Notification scenarios include:

- Successful account transfers.
- Failed transactions.
- OTP generation.
- Suspicious activity.
- Payment success and failure.
- Refund completion.

Email delivery uses Spring `JavaMailSender` and SMTP.

---

## 💸 Internal Account-to-Account Transfer Flow

Internal transfers move funds between two existing Avionyx accounts.

Unlike external deposits, these transactions do not require Razorpay.

### Transfer Lifecycle

1. The user submits sender account number, receiver account number, and amount.
2. API Gateway forwards the request to Transaction Service.
3. Transaction Service validates the request and debits the sender through Account Service using OpenFeign.
4. A transaction record is created with `PROCESSING` status.
5. Transaction Service publishes `transaction.initiated` to Kafka.
6. Fraud Detection Service consumes the event and evaluates the transaction.
7. The service publishes either a clean result or a verification request.
8. Clean transactions are completed automatically.
9. Suspicious transactions require OTP verification.
10. After successful verification, Transaction Service marks the transaction as completed and publishes `transaction.completed`.
11. Account Service consumes the completion event and credits the receiver.
12. Notification Service consumes the event and generates a transaction notification.

### Transaction State Management

| State | Description |
|---|---|
| `PROCESSING` | Transaction initiated and fraud analysis pending. |
| `PENDING_VERIFICATION` | Additional OTP verification required. |
| `COMPLETED` | Transaction successfully completed. |
| `FLAGGED` | Transaction rejected or identified as suspicious. |
| `REFUNDED` | Sender's previously debited amount has been restored. |
| `FAILED` | Transaction failed during processing. |

---

## 🔄 SAGA-Style Compensation

Since each microservice maintains its own database, a transaction spanning multiple services cannot rely on a single traditional database transaction.

Avionyx uses a SAGA-style approach with compensating operations to handle failures.

### Example: Failed Internal Transfer

Suppose a user transfers ₹5,000.

1. Transaction Service requests Account Service to debit ₹5,000 from the sender.
2. Account Service successfully deducts the amount.
3. Transaction Service publishes `transaction.initiated`.
4. Fraud Detection Service identifies suspicious activity.
5. OTP verification fails or expires.
6. Transaction Service initiates compensation.
7. Account Service credits ₹5,000 back to the sender.
8. Transaction status is updated to `FLAGGED` or `REFUNDED`.
9. `transaction.refunded` is published.
10. Notification Service generates a refund notification.

**Important:** Compensation reverses the earlier business operation. It is not a database rollback across all microservices.

---

## 💳 Razorpay External Deposit Flow

Razorpay is used to collect money from external payment sources and add it to an existing Avionyx account.

For example, a user wants to add ₹5,000 to their Avionyx account using UPI or a debit card.

### Payment Lifecycle

1. The frontend sends the target Avionyx account number and amount to Payment Service.
2. Payment Service creates a Razorpay order.
3. The internal payment record is saved with `CREATED` status.
4. Razorpay Checkout opens on the frontend.
5. The user completes the payment using a supported payment method.
6. Razorpay sends a payment event through its webhook.
7. Payment Service verifies the webhook signature and confirms the payment.
8. The payment enters the fraud evaluation workflow.
9. Fraud Detection Service evaluates the deposit according to the configured deposit rules.
10. If approved, Payment Service publishes the approved payment event.
11. Account Service consumes the event and credits the target Avionyx account.
12. Payment status is updated to `COMPLETED`.
13. Notification Service generates a successful deposit notification.

### External Deposit Fraud Handling

If a captured external payment is identified as fraudulent:

1. Do not credit the Avionyx account.
2. Mark the payment for refund processing.
3. Payment Service calls Razorpay's Refund API using the Razorpay payment ID.
4. Update the internal payment status according to the refund response.
5. Publish a refund event.
6. Notification Service generates a refund notification.

In Razorpay Test Mode, refunds are simulated. No real money is transferred.

**Important distinction:**

| Scenario | Compensation |
|---|---|
| Failed internal transfer | Credit the sender's Avionyx account. |
| Rejected external deposit | Request a refund through Razorpay. |

An external deposit should never be treated as completed merely because the frontend Checkout handler returned successfully. Backend verification is required.

---

## 🛡️ Real-Time Fraud Detection

The Fraud Detection Service evaluates transactions using three configurable rules backed by Redis.

### 1. Velocity Check

Detects excessive transaction activity within a specified time window.

### 2. Unusual Amount Check

Identifies transactions that significantly exceed an account's normal transaction amount baseline.

### 3. Balance Percentage Check

Flags transactions that exceed a configured percentage of the sender's available balance.

### Fraud Decision

| Result | Action |
|---|---|
| All checks pass | Publish `fraud.check.clean`. |
| One or more checks fail | Publish `verification.required`. |
| OTP is valid | Allow the transaction to complete. |
| OTP is invalid or expired | Compensate the transaction. |
| Configured serious fraud condition | Trigger account-blocking workflow. |

Fraud rules should be configurable through application properties rather than hardcoded throughout the service.

---

## 🔐 OTP Verification

OTP verification provides an additional security layer for suspicious transactions.

### OTP Lifecycle

1. Fraud Detection Service identifies a suspicious transaction.
2. Transaction Service receives `verification.required`.
3. Transaction Service generates a one-time password.
4. OTP is stored in Redis with a configured expiration time.
5. Transaction Service publishes `transaction.otp.generated`.
6. Notification Service consumes the event and sends the OTP notification.
7. User submits the OTP.
8. Transaction Service retrieves and validates the stored OTP.
9. If valid, the transaction proceeds to completion.
10. If invalid or expired, the transaction is compensated.

Redis is used because OTPs are temporary values that require automatic expiration.

Example Redis key:

```text
transaction:otp:{transactionId}
```

The OTP expiration period should be configurable.

---

## 📡 Kafka Event-Driven Communication

Apache Kafka is used for asynchronous communication between microservices.

Instead of requiring every service to call another service directly, a service can publish an event that interested consumers process independently.

### Kafka Topics

| Topic | Producer | Consumer | Purpose |
|---|---|---|---|
| `transaction.initiated` | Transaction Service | Fraud Detection Service | Starts fraud analysis. |
| `fraud.check.clean` | Fraud Detection Service | Transaction Service | Indicates that fraud checks passed. |
| `verification.required` | Fraud Detection Service | Transaction Service | Requests OTP verification. |
| `transaction.otp.generated` | Transaction Service | Notification Service | Triggers OTP notification. |
| `transaction.completed` | Transaction Service | Account, Notification Services | Completes transfer and triggers notifications. |
| `fraud.detected` | Transaction Service | Account Service | Triggers account-blocking logic. |
| `transaction.refunded` | Transaction Service | Notification Service | Signals internal transfer compensation. |
| `payment.completed` | Payment Service | Account, Notification Services | Credits an approved external deposit and notifies the user. |
| `payment.failed` | Payment Service | Notification Service | Signals payment failure. |
| `payment.refunded` | Payment Service | Notification Service | Signals external payment refund completion. |

---

## ⚡ Redis Usage

Redis is used for fast access to temporary and frequently updated data.

| Use Case | Purpose |
|---|---|
| Transaction Velocity | Count transactions within a time window. |
| Average Transaction Amount | Maintain transaction amount baselines. |
| OTP Storage | Store OTPs with automatic expiration. |
| API Rate Limiting | Control incoming request frequency. |

Redis reduces the need for repeated database queries in time-sensitive fraud checks.

---

## 🗄️ Database Design

Each microservice owns its own database to maintain service independence.

| Database | Owner | Example Data |
|---|---|---|
| `account_db` | Account Service | Account details, balances, account status. |
| `transaction_db` | Transaction Service | Transfers, transaction status, reference numbers. |
| `fraud_db` | Fraud Detection Service | Persistent fraud records and configuration, if required. |
| `payment_db` | Payment Service | Payment records, Razorpay order IDs, payment IDs, refund details. |

PostgreSQL is used as the primary relational database.

Services should access their own database rather than directly querying another service's database.

---

## 🔌 API Endpoints

The following are representative endpoints for the application. Actual paths should match the implemented controllers.

### Account Service

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/accounts` | Create an account. |
| `GET` | `/accounts/{accountNumber}` | Retrieve account details. |
| `GET` | `/accounts/{accountNumber}/balance` | Check account balance. |
| `PUT` | `/accounts/{accountNumber}/credit` | Credit an account. |
| `PUT` | `/accounts/{accountNumber}/debit` | Debit an account. |
| `PUT` | `/accounts/{accountNumber}/block` | Block an account. |

### Transaction Service

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/transaction/transfer` | Initiate an account transfer. |
| `POST` | `/transaction/verify-otp` | Verify a suspicious transaction's OTP. |
| `GET` | `/transaction/{id}` | Retrieve transaction details. |

### Payment Service

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/payments/create-order` | Create a Razorpay payment order. |
| `POST` | `/payments/verify` | Verify Checkout payment signature, if implemented. |
| `POST` | `/payments/webhook` | Receive Razorpay webhook events. |

---

## 🛠️ Technology Stack

| Category | Technologies |
|---|---|
| Programming Language | Java |
| Backend Framework | Spring Boot |
| Persistence | Spring Data JPA, Hibernate |
| Database | PostgreSQL |
| Inter-Service Communication | Spring Cloud OpenFeign |
| Messaging | Apache Kafka |
| Caching | Redis, Spring Data Redis |
| API Gateway | Spring Cloud Gateway |
| Payment Integration | Razorpay Java SDK |
| Notifications | Spring Mail, JavaMailSender, SMTP |
| Code Reduction | Lombok |
| Object Mapping | ModelMapper |
| Build Tool | Maven |
| Containerization | Docker, Docker Compose |
| API Testing | Postman |
| IDE | IntelliJ IDEA |

---

## 🎯 Project Objective

Avionyx demonstrates the design and implementation of a distributed banking application using modern Java backend technologies.

The project focuses on practical distributed-systems concepts, including:

- Microservices architecture.
- Synchronous and asynchronous service communication.
- Event-driven architecture with Kafka.
- Redis-backed real-time fraud detection.
- SAGA-style compensation.
- Secure external payment integration.
- Distributed transaction lifecycle management.
- Idempotency and failure handling.

The goal is to build a modular, scalable, and reliable banking backend while understanding how real-world financial workflows are coordinated across independent services.