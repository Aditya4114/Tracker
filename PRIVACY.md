# Privacy Policy

**Last Updated:** September 8, 2026

Welcome to **Job Tracker** ("we", "our", or "the Application"). We are dedicated to respecting your privacy and protecting the personal information you share with us. This Privacy Policy explains what data we collect, how we process and store it, and your rights concerning your personal data.

---

## 1. Information We Collect

### A. Account Credentials
When you create an account natively, we collect:
- Username
- Email address
- Password (salted and securely hashed with BCrypt)

### B. Google User Data (Restricted Scope: `gmail.readonly`)
When you authenticate via Google OAuth and choose to synchronize your job applications, Job Tracker requests read-only access to your Gmail account (`https://www.googleapis.com/auth/gmail.readonly`). We collect and process:
- **Email Headers:** Sender address, recipient, subject line, date received, and Gmail message identifier (`messageId`).
- **Email Body:** Body text strictly truncated to the first 5,000 characters for job application context parsing.
- **OAuth Refresh Tokens:** Used exclusively to obtain short-lived access tokens to fetch relevant messages when you initiate a sync.

---

## 2. How We Use Google User Data

Job Tracker accesses Google user data solely to provide and improve job application tracking functionality. Specifically:
- **Targeted Keyword Search:** We query your inbox exclusively for job status keywords (e.g., `subject:("status update" OR "application update" OR "application for" OR "thank you for applying")`).
- **Entity Extraction:** We extract structured fields (Company Name, Position/Role, Job ID, Application Date, Application Status).
- **AI-Assisted Cross-Validation:** The truncated body excerpt (maximum 5,000 characters) and regex-extracted fields are transmitted to the **Google Gemini API** (`gemini-3.5-flash-lite`) solely to parse and correct missing entity fields.
- **Safety Net Review:** Ambiguous emails flagged by our local Bayesian classifier are presented in your private dashboard for manual user confirmation before being cataloged.

---

## 3. Google API Services User Data Policy & Limited Use Disclosure

> **Job Tracker's use and transfer to any other app of information received from Google APIs will adhere to the [Google API Services User Data Policy](https://developers.google.com/terms/api-services-user-data-policy), including the Limited Use requirements.**

In strict adherence to these requirements:
1. **No Human Access:** No human employees, contractors, or administrators read your emails, unless you provide explicit consent to investigate a specific technical error or as required by law.
2. **No Advertising or Marketing:** We do NOT use, transfer, or disclose your Google user data for serving personalized, retargeted, or interest-based advertisements.
3. **No Selling or Transfer of Data:** We do NOT sell, rent, or transfer your Google user data or email contents to third parties, data brokers, or advertisers under any circumstances.
4. **No AI/ML Model Training:** Your email data is NOT used to train, retrain, fine-tune, or improve generalized machine learning or artificial intelligence models (including Google Gemini models). Data sent to Gemini API via enterprise endpoints is processed transiently for parsing and is not retained for training.

---

## 4. Data Storage and Security

- **Encryption at Rest:** All Google OAuth refresh tokens are encrypted using AES-256 before being persisted to the database.
- **Password Security:** Passwords are never stored in plaintext; they are hashed using industry-standard BCrypt.
- **Session Tokens:** API interactions are authenticated via signed JSON Web Tokens (JWT) with configured expiration periods.
- **Isolated Databases:** Extracted job records and status logs are strictly tied to your authenticated user account.

---

## 5. Data Retention and Deletion (GDPR & CCPA Rights)

### A. Your Rights
Under applicable data protection laws (including the General Data Protection Regulation (GDPR) and the California Consumer Privacy Act (CCPA/CPRA)), you have the right to:
- Access the personal information we hold about you.
- Request correction of inaccurate information.
- Request permanent deletion ("Right to Erasure") of your account and all associated job records.
- Export your tracked job data in a portable format.

### B. Revoking Google Account Access
You can revoke Job Tracker's access to your Gmail account at any time via:
- Google Account Security Settings: [https://myaccount.google.com/permissions](https://myaccount.google.com/permissions)

Upon revocation, Job Tracker can no longer fetch emails, and any cached access tokens become immediately void.

### C. Requesting Account Deletion
To delete your account and permanently purge all stored data (including email IDs, company tracking records, and encrypted tokens), contact us at `privacy@jobtracker.app`. Account data is purged within 30 days of verification.

---

## 6. Cookies and Local Storage

Job Tracker uses minimal, strictly necessary local storage items (`auth-token` and `username`) to maintain your authenticated session. We do not use third-party tracking or advertising cookies. For details, refer to our [Cookie Policy](COOKIE_POLICY.md).

---

## 7. Changes to This Policy

We may update this Privacy Policy periodically to reflect changes in our practices or regulatory obligations. We will notify users of significant changes by updating the "Last Updated" date at the top of this document.

---

## 8. Contact Us

If you have questions, concerns, or requests regarding this Privacy Policy or our data handling practices, contact our Data Protection Officer at:
- **Email:** `privacy@jobtracker.app`
- **GitHub Issues:** [Repository Issues](https://github.com/)
