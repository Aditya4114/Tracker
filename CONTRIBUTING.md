# Contributing to Job Tracker

Thank you for your interest in contributing to Job Tracker! We welcome bug reports, feature suggestions, documentation enhancements, and pull requests.

---

## Code of Conduct

We are committed to providing a welcoming, inclusive, and harassment-free environment for all contributors. Please treat everyone with respect and kindness.

---

## How Can You Contribute?

### 1. Reporting Bugs
- Check existing [GitHub Issues](https://github.com/) to verify the bug has not already been reported.
- If not reported, open a new issue using the **Bug Report** template.
- Provide a clear, reproducible example with operating system, Java version, and browser information.
- **NEVER** paste real email contents, OAuth credentials, or API keys into issue descriptions.

### 2. Suggesting Features
- Open an issue using the **Feature Request** template.
- Describe the problem you are solving, your proposed solution, and any alternative solutions you evaluated.

### 3. Pull Requests (PRs)
- Fork the repository and create a new topic branch from `main`:
  ```bash
  git checkout -b feature/your-feature-name
  ```
- Follow the repository's coding standards:
  - **Backend:** Java 17+, standard Spring Boot idioms, clean service-layer boundaries, comprehensive unit tests.
  - **Frontend:** Angular standalone components, TypeScript strict mode, responsive CSS.
- **Security Check:** Ensure NO credentials, API keys, or personal tokens are included in your commit history.
- Run builds locally before pushing:
  ```bash
  # Backend
  cd backend && ./mvnw clean test

  # Frontend
  cd frontend && npm run build && npm test
  ```
- Open a Pull Request referencing the related issue number.

---

## Commit Message Conventions

We recommend standard conventional commit prefixes:
- `feat:` A new feature
- `fix:` A bug fix
- `docs:` Documentation updates
- `style:` Formatting, missing semicolons, etc.
- `refactor:` Code refactoring without changing functionality
- `test:` Adding or updating tests
- `chore:` Maintenance, build scripts, dependency updates

---

## Security Vulnerabilities

If you discover a security vulnerability, please do NOT file a public issue. Refer to our [Security Policy](SECURITY.md) for private disclosure instructions.
