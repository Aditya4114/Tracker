# Job Tracker 

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Java: 17](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/)
[![Spring Boot: 4](https://img.shields.io/badge/Spring%20Boot-4.1-green.svg)](https://spring.io/projects/spring-boot)
[![Angular: 21](https://img.shields.io/badge/Angular-21-red.svg)](https://angular.dev)
[![PostgreSQL: 15](https://img.shields.io/badge/PostgreSQL-15-blue.svg)](https://www.postgresql.org/)
[![AI: Google Gemini](https://img.shields.io/badge/AI-Google%20Gemini-purple.svg)](https://ai.google.dev/)
[![Security: Gitleaks](https://img.shields.io/badge/Security-Gitleaks%20Scan-brightgreen.svg)](.github/workflows/secret-scan.yml)

**Job Tracker** is an automated, privacy-first career utility designed to streamline job search management. By integrating directly with your Gmail account via Google OAuth 2.0, Job Tracker scans and catalogs job application updates (Applied, Interviewing, Rejected, Offer), parses company and position details using a multi-tiered heuristic regex and Gemini AI pipeline, and provides an actionable metrics dashboard.

---

## System Architecture

```mermaid
graph TD
    subgraph Frontend ["Angular 21 Frontend (localhost:4200)"]
        UI[Dashboard & Metrics UI]
        AuthUI[Login / Register & Google OAuth]
        SyncUI[Manual Sync Widget - max 30 days]
        SafetyNetUI[Unconfirmed Email Review Queue]
        LegalUI[Privacy, Terms & Cookie Consent Center]
    end

    subgraph Backend ["Spring Boot 4 Backend (localhost:8080)"]
        Security[Spring Security + JWT + AES-256]
        OAuthHandler[Google OAuth2 Client Resolver]
        GmailSvc[Gmail REST API Service]
        Classifier[Bayesian Keyword & Domain Classifier]
        Parser[Regex Heuristic Extraction Engine]
        GeminiClient[Google Gemini AI Cross-Validator]
    end

    subgraph Database ["PostgreSQL 15 (Docker)"]
        Users[(Users)]
        Tokens[(OAuthTokens - AES Encrypted)]
        Applications[(Job Applications)]
        ReviewQueue[(Pending Review Emails)]
        Processed[(Processed Email Dedup IDs)]
    end

    subgraph External ["External Cloud APIs"]
        GCP[Google Cloud OAuth 2.0]
        GmailAPI[Gmail API - readonly scope]
        GeminiAPI[Google Gemini Flash API]
    end

    UI -->|REST + Bearer JWT| Security
    AuthUI -->|OAuth Redirect| OAuthHandler
    OAuthHandler <-->|Authorize / Exchange Tokens| GCP
    SyncUI -->|Trigger Sync Request| GmailSvc
    GmailSvc <-->|Fetch Job Messages| GmailAPI
    GmailSvc --> Classifier
    Classifier -->|Ambiguous| ReviewQueue
    Classifier -->|Confirmed Job Email| Parser
    Parser -->|Truncated 5k excerpt| GeminiClient
    GeminiClient <-->|Entity Correction| GeminiAPI
    Parser --> Applications
    Security --> Users
    Security --> Tokens
    ReviewQueue --> SafetyNetUI
```

---

## Key Features

### 1. Career Pipeline & Real-Time Analytics Dashboard
- **Dynamic Metrics Grid:** Instant visibility into key job search metrics for any selected date range:
  - **Total Applications:** Total volume of tracked submissions.
  - **In-Progress Applications:** Active opportunities currently in review or interview stages.
  - **Rejection Counter:** Consolidated count of closed applications.
  - **Calculated Response Rate:** Real-time percentage of applications that received an employer response versus unanswered applications.
- **Customizable Date-Range Window:** Interactive date pickers allowing sync and metric evaluation across custom date windows (from 7 days up to a 30-day safety limit).

---

### 2. Multi-Tab Applications Hub & Triage Queues
- **Human-in-the-Loop (HITL) Safety Net Queue:**
  - Ambiguous or non-standard emails that cannot be classified with high confidence are routed to an **Unconfirmed Emails** queue.
  - Displays sender address, subject line, and an excerpt snippet.
  - **One-Click Triage:** Reviewers can click **`✔ Job`** to confirm and parse the application or **`✖ Not Job`** to dismiss it.
  - Dynamic badge counters alert users when unconfirmed emails require attention.
- **Needs Field Edit Queue:**
  - Automatically isolates parsed applications that have missing or ambiguous fields (such as missing Company Name or Job Title marked as "Not Available").
  - Provides a built-in modal editor for fast manual field corrections.
- **Direct Gmail Deep-Linking:**
  - Every application record includes a direct `Open in Gmail` link targeting the exact Gmail message ID (`https://mail.google.com/mail/u/0/#all/{messageId}`), enabling users to view the original email context in one click.
- **False-Positive "Mark as Spam" Action:**
  - Users can mark any wrongly captured application as spam directly from the dashboard, removing the record and training the classifier to ignore similar emails in the future.

---

### 3. Self-Training Bayesian Machine Learning Engine
- **Continuous Online Active Learning:**
  - User feedback from the Safety Net queue and "Mark as Spam" triggers immediate, in-memory updates to the Naive Bayes vocabulary and class probability distributions.
  - Model states and token counts are persisted asynchronously to PostgreSQL (`GlobalModelState`), allowing the classifier to become progressively smarter over time.
- **Fast-Pass ATS Recognition:**
  - Built-in recognition for dedicated Applicant Tracking Systems (ATS) including Greenhouse, Lever, Workday, Ashby, SmartRecruiters, iCIMS, Taleo, BambooHR, Jobvite, Breezy HR, Rippling, and Workable.
- **Safety Net Invariant:**
  - An email is **never silently dropped** unless its sender domain has been confirmed as spam at least twice. Uncertain emails always fall back to the user review queue.

---

### 4. Intelligent Incremental Sync & Quota Optimization
- **Contiguous Date Range Grouping:**
  - `DailySyncLog` tracks all calendar dates already synchronized for each user.
  - When a sync is requested, the system computes only the **missing/unsynced dates**, groups them into contiguous query blocks, and executes minimal, targeted Gmail API calls.
  - Conserves Gmail API quotas and accelerates sync performance.
- **Strict Deduplication Pipeline:**
  - Skips emails if their Gmail Message ID has already been recorded in `ProcessedEmail`.
  - Collision avoidance: Skips duplicate entries if an application for the same company has already been logged on the same calendar day.
- **Asynchronous Execution:**
  - Sync tasks run asynchronously in the background (`@Async` with `CompletableFuture`), providing non-blocking UI interactions and live status feedback.

---

### 5. Multi-Stage AI & Heuristic Parsing Pipeline
- **Heuristic Regex Extraction:** Instant, local extraction of company names, job titles, and status keywords from email subjects and bodies.
- **Gemini AI Cross-Validation:**
  - Automatically truncates email bodies to 5,000 characters to optimize token efficiency and latency.
  - Sends the excerpt and preliminary regex data to Google Gemini Flash API (`gemini-3.5-flash-lite`) to validate ambiguous company names, recover obscure job titles, and normalize application statuses.
  - Robust fallback: If the Gemini API hits a rate limit or network issue, the pipeline gracefully falls back to local regex extraction without dropping data.

---

### 6. Application Event Lifecycle & Timeline
- Extensible event timeline model (`ApplicationEvent`) that logs historical status transitions (e.g., `Applied` ➔ `Interview` ➔ `Offer` / `Rejected`) with timestamps for every tracked job application.

---

## Prerequisites

Before running the project locally, ensure you have the following installed:

- **Java Development Kit (JDK):** Version 17 or higher (`java -version`)
- **Node.js & npm:** Node.js 20+ and npm 10+ (`node -v`, `npm -v`)
- **Docker & Docker Compose:** For running PostgreSQL (`docker compose version`)
- **Google Cloud Console Project:** With the Gmail API enabled
- **Google AI Studio API Key:** For Gemini AI parsing

---

## Quickstart Local Setup

### 1. Clone the Repository
```bash
git clone https://github.com/<your-username>/Tracker.git
cd Tracker
```

### 2. Configure Environment Variables
Copy the template configuration file:
```bash
cp .env.example .env
```
Open `.env` and fill in your values (database password, Google Client ID/Secret, Gemini API Key, AES encryption key).

### 3. Start PostgreSQL Database
```bash
docker compose up -d
```
Verify the container is running:
```bash
docker ps --filter "name=job_tracker_db"
```

### 4. Start the Spring Boot Backend
Navigate to the `backend` folder and run with the Maven wrapper:
```bash
cd backend
# On Linux / macOS:
./mvnw spring-boot:run

# On Windows (PowerShell / Command Prompt):
.\mvnw.cmd spring-boot:run
```
The backend will launch on `http://localhost:8080`.

### 5. Start the Angular Frontend
In a separate terminal, navigate to the `frontend` folder:
```bash
cd frontend
npm install
npm start
```
Open your browser and navigate to `http://localhost:4200`.

---

## Google Cloud OAuth 2.0 Setup Guide

Because Job Tracker interacts with Gmail using the restricted `gmail.readonly` scope, configure Google Cloud Console as follows:

### Step 1: Create a Project & Enable Gmail API
1. Navigate to the [Google Cloud Console](https://console.cloud.google.com/).
2. Create a new project named **Job Tracker**.
3. Go to **APIs & Services > Library**, search for **Gmail API**, and click **Enable**.

### Step 2: Configure OAuth Consent Screen
1. Go to **APIs & Services > OAuth consent screen**.
2. Select **User Type: External** and click **Create**.
3. Fill in:
   - **App name:** Job Tracker
   - **User support email:** Your email address
   - **Developer contact information:** Your email address
4. Under **Scopes for Google APIs**, click **Add or Remove Scopes** and add:
   - `https://www.googleapis.com/auth/gmail.readonly`
   - `openid`
   - `profile`
   - `email`
5. Under **Test Users**, add the Gmail addresses you will use for testing (while in Google "Test Mode", refresh tokens expire after 7 days).

### Step 3: Create Web OAuth Client Credentials
1. Go to **APIs & Services > Credentials > Create Credentials > OAuth client ID**.
2. Select **Application type:** Web application.
3. Name: `Job Tracker Web Client`.
4. Set **Authorized JavaScript origins**:
   - `http://localhost:4200`
5. Set **Authorized redirect URIs**:
   - `http://localhost:8080/login/oauth2/code/google`
6. Copy the generated **Client ID** and **Client Secret** into your `.env` file (`GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`).

---

## Google Gemini AI Setup

1. Visit [Google AI Studio](https://aistudio.google.com/).
2. Click **Get API key** and generate a new key.
3. Paste the key into your `.env` file under `GEMINI_API_KEY`.

---

## Environment Variables Reference

| Variable Name | Description | Default / Example |
| :--- | :--- | :--- |
| `POSTGRES_DB` | PostgreSQL database name | `jobtracker` |
| `POSTGRES_USER` | PostgreSQL user | `tracker_user` |
| `POSTGRES_PASSWORD` | PostgreSQL password | *Set in `.env`* |
| `SPRING_DATASOURCE_URL` | JDBC connection URL | `jdbc:postgresql://localhost:5432/jobtracker` |
| `JWT_SECRET` | Secret key for signing JWT tokens (min 32 chars) | *Set in `.env`* |
| `JWT_EXPIRATION_MS` | JWT token validity in milliseconds | `86400000` (24h) |
| `GOOGLE_CLIENT_ID` | OAuth 2.0 Web Client ID from Google Cloud | *Set in `.env`* |
| `GOOGLE_CLIENT_SECRET` | OAuth 2.0 Web Client Secret from Google Cloud | *Set in `.env`* |
| `GEMINI_API_KEY` | API Key from Google AI Studio | *Set in `.env`* |
| `GEMINI_API_URL` | Endpoint for Gemini model generation | `https://generativelanguage.googleapis.com/...` |
| `ENCRYPTION_SECRET` | 16, 24, or 32-byte key for AES-256 token encryption | *Set in `.env`* |

---

## Security & Compliance

- **Zero Hardcoded Secrets:** All credentials, keys, and tokens are read strictly from environment variables or ignored `.env` files.
- **Automated Secret Scanning:** Continuous integration scans all commits using [Gitleaks](https://github.com/gitleaks/gitleaks) to prevent accidental credential leakage.
- **Google Limited Use Compliance:** Job Tracker adheres strictly to the [Google API Services User Data Policy](https://developers.google.com/terms/api-services-user-data-policy), including the Limited Use requirements.
- **Responsible Disclosure:** Found a vulnerability? Please review our [Security Policy](SECURITY.md) before reporting.

---

## Legal & Policies

- [Privacy Policy](PRIVACY.md)
- [Terms and Conditions](TERMS.md)
- [Cookie & Local Storage Policy](COOKIE_POLICY.md)
- [Security Policy](SECURITY.md)
- [Contributing Guide](CONTRIBUTING.md)

---

## License

This project is licensed under the [MIT License](LICENSE).
