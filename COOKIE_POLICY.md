# Cookie & Local Storage Policy

**Last Updated:** September 8, 2026

This Cookie & Local Storage Policy explains how **Job Tracker** ("we", "us", or "our") uses cookies and similar web browser storage mechanisms (specifically `localStorage` and `sessionStorage`) when you interact with our web application.

---

## 1. What Are Cookies and Local Storage?

- **Cookies:** Small text files placed on your browser or device by websites you visit to enable fundamental functionality and remember preferences.
- **Local Storage (`localStorage`):** A web browser mechanism that allows web applications to store key-value data locally within the user's browser with no expiration date, enabling persistent sessions across page refreshes.

---

## 2. How Job Tracker Uses Browser Storage

Job Tracker is designed with a **privacy-first architecture**. We do **NOT** use third-party analytics trackers, advertising beacons, tracking pixels, or cross-site fingerprinting technologies.

We categorize our browser storage into two types:

### A. Strictly Necessary (Essential) Storage
These items are mandatory for the application to function securely. Without them, you cannot log in or navigate the authenticated dashboard.

| Key Name | Storage Mechanism | Purpose | Duration | Category |
| :--- | :--- | :--- | :--- | :--- |
| `auth-token` | `localStorage` | Secure JWT (JSON Web Token) used to authenticate API requests to the backend. | Persists until manual logout or token expiration (24h default). | Strictly Necessary |
| `username` | `localStorage` | Displays your active user identifier in the navigation bar and profile context. | Persists until manual logout. | Strictly Necessary |
| `cookie-consent` | `localStorage` | Records your cookie consent choice ("accepted", "essential-only") so you are not prompted on every visit. | 1 Year | Strictly Necessary |

### B. Functional & Preference Storage
These items remember your preferences to improve your personal experience.

| Key Name | Storage Mechanism | Purpose | Duration | Category |
| :--- | :--- | :--- | :--- | :--- |
| `job_tracker_sync_range` | `localStorage` | Remembers your preferred default sync window (e.g., last 7 days vs last 30 days). | Optional / User Session | Functional |
| `theme-preference` | `localStorage` | Stores visual display preferences (light/dark mode if enabled). | Optional | Functional |

---

## 3. Third-Party Cookies

- **Google OAuth:** When you choose to authenticate via Google OAuth, Google may set its own session cookies on Google-owned domains (`accounts.google.com`) during the authentication flow. These cookies are governed by [Google's Privacy Policy](https://policies.google.com/privacy). Job Tracker has no control over or access to Google's third-party domain cookies.
- **No Advertisers:** We do not partner with or embed any third-party advertising networks (e.g., Google AdSense, Meta Pixel).

---

## 4. How Can You Manage Cookie Settings?

You have full control over how cookies and storage items are handled:

### In-App Cookie Consent Banner
When you first visit Job Tracker, an interactive consent banner allows you to:
- **Accept All:** Enables both essential authentication tokens and functional preferences.
- **Essential Only:** Restricts browser storage strictly to necessary authentication tokens (`auth-token` and `username`).
- **Cookie Settings:** Modify and update your saved preferences at any time via the "Cookie Settings" link in the footer.

### Browser Controls
You can configure your browser to block or alert you about cookies, or clear your browsing data and `localStorage`:
- **Google Chrome:** Settings > Privacy and Security > Cookies and other site data
- **Mozilla Firefox:** Settings > Privacy & Security > Cookies and Site Data
- **Apple Safari:** Preferences > Privacy > Manage Website Data
- **Microsoft Edge:** Settings > Cookies and site permissions

*Note: If you clear or disable `localStorage`, you will be logged out and will need to sign in again.*

---

## 5. Contact Us

If you have questions about our use of cookies or local storage, please reach out to us at:
- **Email:** `privacy@jobtracker.app`
