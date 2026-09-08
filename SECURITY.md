# Security Policy

The Job Tracker team is committed to ensuring the safety, privacy, and security of all user data, especially credentials, OAuth tokens, and email metadata processed through Google APIs.

## Supported Versions

Only the latest released version on the `main` branch receives active security updates and vulnerability patches.

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |
| < 1.0   | :x:                |

## Reporting a Vulnerability

**Please do not report security vulnerabilities through public GitHub issues.**

If you believe you have found a security vulnerability in Job Tracker, please report it privately:

1. **Email**: Send detailed vulnerability reports to `security@jobtracker.app` (or contact the repository maintainers via GitHub private security advisories).
2. **Details to Include**:
   - Description of the vulnerability and its potential impact.
   - Exact steps or proof-of-concept (PoC) code to reproduce the issue.
   - Any proposed mitigations or code fixes.
   - The commit hash or version you tested.

### What to Expect
- **Acknowledgment**: You will receive an acknowledgment of your report within 48 hours.
- **Triage & Status**: We will provide an assessment of the vulnerability and keep you informed of progress toward a fix within 7 days.
- **Resolution**: Once patched, a security release will be published along with appropriate attribution (unless you prefer anonymity).

## Secrets Management & Code Hygiene

- **Zero Hardcoded Secrets Policy**: Never commit real database credentials, API keys, private keys, or OAuth client secrets into source control.
- **Environment Isolation**: Always use `.env` files locally (which are strictly ignored by `.gitignore`) and secret stores in production environments.
- **Automated Scanning**: All pull requests and commits are scanned using automated secret detection tooling (`gitleaks`). Any commits containing suspected secrets will be blocked.
- **Token Protection**: User OAuth refresh tokens are encrypted at rest using AES-256 before being stored in the database.
