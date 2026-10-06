# Enterprise Kubernetes Deployment & Operations Handbook

Welcome to the **Job Tracker Microservices Kubernetes Handbook**. This document provides an exhaustive, industry-standard operational guide for building, containerizing, deploying, scaling, and managing the event-driven Job Tracker architecture in Kubernetes.

---

## 1. System Architecture Overview

The system is structured as an **Event-Driven Microservices Architecture** backed by **Apache Kafka (KRaft)**, **Spring Cloud Gateway**, and **Isolated PostgreSQL Databases**.

```
[ Frontend / React Client ]
              │
              ▼
   [ Spring Cloud Gateway ] (:8000)
    │           │           │          │
    │           │           │          └────────────────┐
    ▼           ▼           ▼                           ▼
[Auth Service] [Ingestion] [Intelligence Review]  [Core Service]
   (:8081)       (:8082)        (:8083)               (:8084)
      │             │              │                     ▲
[tracker_auth]      │              │                     │
                    ▼              │                     │
               Kafka Topic:        ▼                     │
           `jobtracker.raw-emails` ────────┐             │
                                           ▼             │
                                 [Intelligence Worker]   │
                                           │             │
                                     Kafka Topic:        │
                                 `jobtracker.parsed-jobs` ─┘
```

### Microservice Inventory

| Service | Port | Database | Primary Responsibility | Event Role |
| :--- | :--- | :--- | :--- | :--- |
| **`gateway`** | 8000 | None | Reverse proxy, CORS, route dispatching | None |
| **`auth-service`** | 8081 | `tracker_auth` | OAuth2, registration, JWT issuance, **Token Vending Machine** | None |
| **`ingestion-service`**| 8082 | `tracker_ingestion` | Gmail API sync, schedule tracking (`DailySyncLog`) | **Producer**: `jobtracker.raw-emails` |
| **`intelligence-service`**| 8083| `tracker_intelligence`| Bayesian Classification, Regex & Gemini LLM parsing, Pending Review | **Consumer**: `jobtracker.raw-emails`<br>**Producer**: `jobtracker.parsed-jobs`<br>**DLQ**: `jobtracker.raw-emails.dlq` |
| **`core-service`** | 8084 | `tracker_core` | Applications CRUD, status updates, metrics dashboard | **Consumer**: `jobtracker.parsed-jobs` |

---

## 2. Kafka Event-Driven Pipeline Specification

### Topics
1. **`jobtracker.raw-emails`** (3 Partitions, 1 Replica locally / 3 in Prod)
   - **Key**: `userId` (ensures strict ordering per user).
   - **Payload**: `RawEmailEvent` (messageId, headers, sender, snippet, full decoded body, timestamp).
2. **`jobtracker.parsed-jobs`** (3 Partitions)
   - **Key**: `userId`.
   - **Payload**: `ParsedJobEvent` (company, position, status, appliedDate, messageId).
3. **`jobtracker.raw-emails.dlq`**
   - **Purpose**: Dead Letter Queue for malformed emails or unrecoverable exceptions.

---

## 3. Pre-requisites & Local Environment Setup

### Required Tooling
- **Docker** 24.0+ & **Docker Compose** v2
- **Kubernetes CLI (`kubectl`)**
- **Minikube** or **Kind** (for local K8s testing) or cloud cluster (AWS EKS / GCP GKE)

### Starting Local Dependencies (Docker Compose)
Before running in Kubernetes, you can verify everything locally with Docker Compose:
```bash
docker compose up -d
```
This spins up:
- PostgreSQL with `init-db.sql` automatically creating all 4 databases.
- Apache Kafka in modern KRaft mode (no Zookeeper).
- Kafka UI at `http://localhost:8089` to inspect events and topics.

---

## 4. Containerizing Microservices

Build all images using the multi-stage Dockerfile located in `backend/`:

```bash
cd backend

# 1. Gateway
docker build --build-arg MODULE=gateway -t tracker/gateway:latest .

# 2. Auth Service
docker build --build-arg MODULE=auth-service -t tracker/auth-service:latest .

# 3. Ingestion Service
docker build --build-arg MODULE=ingestion-service -t tracker/ingestion-service:latest .

# 4. Intelligence Service
docker build --build-arg MODULE=intelligence-service -t tracker/intelligence-service:latest .

# 5. Core Service
docker build --build-arg MODULE=core-service -t tracker/core-service:latest .
```

> **Minikube Tip**: If deploying into Minikube, run `eval $(minikube docker-env)` before building so the images are directly accessible within your Minikube cluster without a remote registry.

---

## 5. Kubernetes Deployment Runbook

All Kubernetes manifests are located in the `k8s/` directory.

### Step 5.1: Create Namespace & Secrets
```bash
kubectl apply -f k8s/00-namespace.yaml
kubectl apply -f k8s/01-configmaps-secrets.yaml
```

### Step 5.2: Deploy Storage & Database (PostgreSQL)
```bash
kubectl apply -f k8s/02-postgres.yaml
# Wait for PostgreSQL to be ready:
kubectl wait --namespace jobtracker --for=condition=ready pod -l app=postgres --timeout=120s
```

### Step 5.3: Deploy Messaging Broker (Apache Kafka)
```bash
kubectl apply -f k8s/03-kafka.yaml
# Wait for Kafka to be ready:
kubectl wait --namespace jobtracker --for=condition=ready pod -l app=kafka --timeout=120s
```

### Step 5.4: Deploy Microservices
Deploy all backend services in order:
```bash
kubectl apply -f k8s/04-auth-service.yaml
kubectl apply -f k8s/05-ingestion-service.yaml
kubectl apply -f k8s/06-intelligence-service.yaml
kubectl apply -f k8s/07-core-service.yaml
```

### Step 5.5: Deploy API Gateway & Ingress
```bash
kubectl apply -f k8s/08-gateway.yaml
```

---

## 6. Verification and Health Checks

### Check Pod Status
```bash
kubectl get pods -n jobtracker -o wide
```

Expected output:
```
NAME                                      READY   STATUS    RESTARTS   AGE
auth-service-xxxx-xxx                     1/1     Running   0          2m
core-service-xxxx-xxx                     1/1     Running   0          2m
gateway-xxxx-xxx                          1/1     Running   0          2m
ingestion-service-xxxx-xxx                1/1     Running   0          2m
intelligence-service-xxxx-xxx             1/1     Running   0          2m
kafka-deployment-xxxx-xxx                 1/1     Running   0          3m
postgres-deployment-xxxx-xxx              1/1     Running   0          4m
```

### Accessing the API Gateway Locally
If using Minikube or standard port-forward:
```bash
kubectl port-forward -n jobtracker service/gateway-service 8000:8000
```
Test endpoints via Gateway:
```bash
# Auth check
curl -X POST http://localhost:8000/api/auth/login -H "Content-Type: application/json" -d '{"username":"test","password":"password"}'

# Dashboard check
curl -X GET http://localhost:8000/api/dashboard -H "X-User-Id: 1"
```

---

## 7. Scaling & High Availability

### Horizontal Pod Autoscaling (HPA)
In enterprise environments, the `intelligence-service` handles CPU-bound LLM and regex processing, while `ingestion-service` handles high I/O burst traffic.

To enable horizontal autoscaling:
```bash
kubectl autoscale deployment intelligence-service --cpu-percent=70 --min=2 --max=10 -n jobtracker
kubectl autoscale deployment ingestion-service --cpu-percent=70 --min=2 --max=6 -n jobtracker
```

### Zero-Downtime Rolling Updates
Deploy updates without downtime using Kubernetes rolling update strategy configured on all deployments:
```bash
kubectl set image deployment/core-service core-service=tracker/core-service:v2 -n jobtracker
kubectl rollout status deployment/core-service -n jobtracker
```

---

## 8. Failure Modes & Disaster Recovery

| Failure Scenario | System Behavior | Recovery Action |
| :--- | :--- | :--- |
| **Gemini API Down / Rate-Limited** | `intelligence-service` retries or buffers in Kafka. No raw emails are lost. | Increase retry backoff or scale consumer pods down temporarily until quota resets. |
| **Ingestion Service Crash** | Kafka topic buffer remains intact; unfinished syncs are resumed on next schedule. | Kubernetes automatically restarts pod via liveness probes. |
| **Dead Letter Queue (DLQ)** | Corrupted emails land in `jobtracker.raw-emails.dlq`. | Inspect payload in Kafka UI / CLI, fix parsing regex, and replay events. |

---
**Maintained by**: Lead Microservices Architect
