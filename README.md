# Digital Banking & Fraud Detection System

A **microservices-based digital banking system** that enables account transfers, detects suspicious transactions in real time, verifies high-risk transfers using OTPs, and handles failed transactions through SAGA-style compensation and refunds. It also supports **Razorpay payments** and **event-driven email notifications** using Kafka.

---

## 🚀 Key Features

- 🏦 Microservices-based digital banking architecture
- 💸 Account-to-account transfer processing
- 🔄 SAGA-style distributed transaction compensation and refunds
- 🛡️ Real-time fraud detection using Redis
- 🔐 OTP-based verification for suspicious transactions
- 🚫 Automatic account blocking for fraudulent activity
- 📡 Kafka-based event-driven communication
- 💳 Razorpay payment integration with webhook processing
- 📧 Event-driven email notifications
- 🐳 Docker-based service deployment

---

## 🏗️ High-Level Architecture

```text
            
                                 ┌──────────────┐
                                 │     User     │
                                 └──────┬───────┘
                                        │
                                        ▼
                                 ┌──────────────┐
                                 │ API Gateway  │
                                 └──────┬───────┘
                                        │
                          ┌─────────────┴─────────────┐
                          │                           │
                          ▼                           ▼
                ┌──────────────────────┐     ┌──────────────────────┐
                │ Transaction Service  │     │   Payment Service    │
                └──────────┬───────────┘     └──────────┬───────────┘
                           │                            │
                      Feign / REST                      │
                           │                            ▼
                           ▼                    ┌─────────────────┐
                ┌──────────────────────┐        │    Razorpay     │
                │   Account Service    │        │  Create Order   │
                │ Deduct Sender Balance│        └────────┬────────┘
                └──────────┬───────────┘                 │
                           │                             ▼
                           ▼                    ┌─────────────────┐
                Transaction = PROCESSING       │ Razorpay Checkout│
                           │                    └────────┬────────┘
                           │                             │
                           │                             ▼
                           │                    ┌─────────────────┐
                           │                    │ Razorpay Webhook│
                           │                    └────────┬────────┘
                           │                             │
                           │                      ┌──────┴──────┐
                           │                      │             │
                           │                 payment.captured  payment.failed
                           │                      │             │
                           │                      ▼             ▼
                           │                payment.completed payment.failed
                           │                      │             │
                           │                      └──────┬──────┘
                           │                             │
                           │                           Kafka
                           │                             │
                           │                             ▼
                           │                    Notification Service
                           │                             │
                           │                       ┌─────┴─────┐
                           │                       ▼           ▼
                           │                  Success Email  Failure Email
                           │
                           │ Kafka
                           ▼
                  transaction.initiated
                           │
                           ▼
                ┌────────────────────────┐
                │  Fraud Detection       │
                │       Service          │
                └───────────┬────────────┘
                            │
                       Redis Checks
                            │
                     ┌──────┴──────┐
                     │             │
                   CLEAN       SUSPICIOUS
                     │             │
                     ▼             ▼
            fraud.check.clean   verification.required
                     │             │
                     │             ▼
                     │       Transaction Service
                     │             │
                     │       Generate OTP
                     │             │
                     │             ▼
                     │           Redis
                     │      (1 min expiry)
                     │             │
                     │             ▼
                     │  transaction.otp-generated
                     │             │
                     │             ▼
                     │    Notification Service
                     │             │
                     │             ▼
                     │         OTP Email
                     │             │
                     │             ▼
                     │         User Verify
                     │             │
                     │        ┌────┴────┐
                     │        │         │
                     │      VALID     INVALID
                     │        │         │
                     │        ▼         ├──────────────────┐
                     │    COMPLETED     │                  │
                     │                  ▼                  ▼
                     │           fraud.detected       COMPENSATE
                     │                  │                  │
                     │                  ▼                  ▼
                     │           Block Account       Refund Sender
                     │                                     │
                     │                                     ▼
                     │                              Transaction FLAGGED
                     │                                     │
                     │                                     ▼
                     │                              transaction.refunded
                     │                                     │
                     │                                     ▼
                     │                             Notification Service
                     │                                     │
                     │                                     ▼
                     │                                 Refund Email
                     │
                     └──────────────────┐
                                        │
                                        ▼
                              transaction.completed
                                        │
                                 ┌──────┴──────┐
                                 │             │
                                 ▼             ▼
                          Account Service  Notification Service
                                 │               |
                                 ▼               ▼
                          Credit Receiver     Email Alert    
```

## 🧩 Microservices

| Service | Responsibility |
|---|---|
| **API Gateway** | Single entry point and request routing |
| **Account Service** | Account creation, balance management, and account blocking |
| **Transaction Service** | Transfer processing, transaction lifecycle, OTP verification, and compensation |
| **Fraud Detection Service** | Real-time fraud checks using Redis |
| **Payment Service** | Razorpay order creation and webhook processing |
| **Notification Service** | Event-driven email notifications |

---

## 🛠️ Tech Stack

**Backend:** Java 17, Spring Boot, Spring Data JPA, Hibernate, Spring Cloud OpenFeign

**Messaging:** Apache Kafka

**Caching:** Redis, Spring Data Redis

**Database:** Postgress

**Payment:** Razorpay

**Notifications:** JavaMailSender / SMTP

**Infrastructure:** Docker, Docker Compose

**Testing:** Postman

**Build Tool:** Maven

---

## 📨 Kafka Events

| Topic | Purpose |
|---|---|
| `transaction.initiated` | Starts fraud analysis |
| `fraud.check.clean` | Indicates fraud checks passed |
| `verification.required` | Requests OTP verification |
| `transaction.otp-generated` | Triggers OTP notification |
| `transaction.completed` | Signals successful transaction |
| `fraud.detected` | Triggers suspicious-account handling |
| `transaction.refunded` | Signals refund/compensation |
| `payment.completed` | Signals successful Razorpay payment |
| `payment.failed` | Signals failed Razorpay payment |


---

## 🛡️ Real-Time Fraud Detection

The Fraud Detection Service evaluates transactions using three configurable rules:

- **Velocity Check** — Detects excessive transactions within a 60-second window using Redis.
- **Unusual Amount Check** — Compares the transaction amount against the account's Redis-based transaction amount baseline.
- **Balance Percentage Check** — Flags transactions exceeding the configured percentage of the sender's available balance.

Suspicious transactions require OTP verification before completion.