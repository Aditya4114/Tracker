import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';

@Component({
  selector: 'app-legal',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div class="legal-page">
      <header class="legal-header">
        <div class="header-content">
          <a routerLink="/login" class="back-link">← Back to Application</a>
          <h1>Legal & Compliance Center</h1>
          <p class="subtitle">Understand how Job Tracker protects your privacy, uses data, and complies with Google API policies.</p>
        </div>
      </header>

      <div class="legal-container">
        <!-- Navigation Tabs -->
        <nav class="legal-tabs">
          <button 
            class="tab-button" 
            [class.active]="activeTab === 'privacy'" 
            (click)="setTab('privacy')">
            Privacy Policy
          </button>
          <button 
            class="tab-button" 
            [class.active]="activeTab === 'terms'" 
            (click)="setTab('terms')">
            Terms of Service
          </button>
          <button 
            class="tab-button" 
            [class.active]="activeTab === 'cookies'" 
            (click)="setTab('cookies')">
            Cookie Policy
          </button>
          <button 
            class="tab-button" 
            [class.active]="activeTab === 'google'" 
            (click)="setTab('google')">
            Google Limited Use
          </button>
        </nav>

        <!-- Tab 1: Privacy Policy -->
        <section *ngIf="activeTab === 'privacy'" class="legal-content">
          <h2>Privacy Policy</h2>
          <p class="last-updated">Last Updated: September 8, 2026</p>

          <p>
            Job Tracker is committed to protecting your privacy and ensuring you have a positive experience on our website and in using our services. 
            This Privacy Policy applies to the Job Tracker web application.
          </p>

          <h3>1. Data We Access and Collect</h3>
          <p>
            When you sign up natively, we collect your username, email address, and an encrypted hash of your password. 
            When you connect your Google account, we request read-only access to your Gmail messages via the <code>gmail.readonly</code> scope.
          </p>
          <ul>
            <li><strong>Email Metadata:</strong> Subject line, sender, date, and message ID.</li>
            <li><strong>Email Body Snippets:</strong> Strictly truncated to the first 5,000 characters to extract job application status updates.</li>
            <li><strong>Encrypted Tokens:</strong> Google OAuth refresh tokens encrypted at rest with AES-256.</li>
          </ul>

          <h3>2. How We Use Your Data</h3>
          <p>
            Data is accessed exclusively when you trigger an email synchronization. The application queries job status emails, 
            extracts company name and position using heuristic regex, and cross-validates ambiguous entries using the Google Gemini API.
          </p>

          <h3>3. No Sale or Advertising</h3>
          <p>
            Job Tracker never sells, rents, or discloses personal information or email contents to third parties or advertising brokers. 
            We do not display advertisements.
          </p>

          <h3>4. User Rights & Account Deletion (GDPR / CCPA)</h3>
          <p>
            You retain full rights to your data. You can disconnect your Google account at any time via 
            <a href="https://myaccount.google.com/permissions" target="_blank" rel="noopener">Google Security Settings</a> 
            or request complete account erasure by contacting <code>privacy@jobtracker.app</code>.
          </p>
        </section>

        <!-- Tab 2: Terms of Service -->
        <section *ngIf="activeTab === 'terms'" class="legal-content">
          <h2>Terms of Service</h2>
          <p class="last-updated">Last Updated: September 8, 2026</p>

          <h3>1. Acceptance of Terms</h3>
          <p>
            By accessing or using Job Tracker, you agree to comply with and be bound by these Terms of Service. If you do not agree, please do not use the application.
          </p>

          <h3>2. Use of Service</h3>
          <p>
            Job Tracker is provided as a career productivity tool to help you organize and catalog your job applications. 
            You agree to use this application only for lawful purposes and in accordance with Google's API Terms of Service.
          </p>

          <h3>3. Accuracy & Disclaimers</h3>
          <p>
            The software is provided on an "AS IS" and "AS AVAILABLE" basis without warranties of any kind. 
            While we utilize advanced pattern matching and AI validation, we do not guarantee that all job emails will be parsed without error. 
            Users are advised to review the parsed applications on the dashboard.
          </p>

          <h3>4. Limitation of Liability</h3>
          <p>
            In no event shall the authors or copyright holders of Job Tracker be liable for any direct, indirect, incidental, 
            or consequential damages arising from the use or inability to use this software.
          </p>
        </section>

        <!-- Tab 3: Cookie Policy -->
        <section *ngIf="activeTab === 'cookies'" class="legal-content">
          <h2>Cookie & Local Storage Policy</h2>
          <p class="last-updated">Last Updated: September 8, 2026</p>

          <p>
            Job Tracker respects your privacy and avoids intrusive tracking. We do not use third-party tracking or advertising cookies.
          </p>

          <h3>Storage Details</h3>
          <table class="policy-table">
            <thead>
              <tr>
                <th>Key</th>
                <th>Storage</th>
                <th>Purpose</th>
                <th>Type</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td><code>auth-token</code></td>
                <td>localStorage</td>
                <td>Session JWT token for secure API communication</td>
                <td>Strictly Necessary</td>
              </tr>
              <tr>
                <td><code>username</code></td>
                <td>localStorage</td>
                <td>Current authenticated user display</td>
                <td>Strictly Necessary</td>
              </tr>
              <tr>
                <td><code>cookie-consent</code></td>
                <td>localStorage</td>
                <td>User's saved cookie consent preferences</td>
                <td>Strictly Necessary</td>
              </tr>
            </tbody>
          </table>

          <p class="mt-4">
            You can modify your consent settings anytime by clicking "Cookie Settings" in the footer.
          </p>
        </section>

        <!-- Tab 4: Google Limited Use -->
        <section *ngIf="activeTab === 'google'" class="legal-content">
          <h2>Google API Services User Data Policy Compliance</h2>
          <p class="last-updated">Restricted Scope: <code>https://www.googleapis.com/auth/gmail.readonly</code></p>

          <div class="callout-box">
            <strong>Limited Use Disclosure:</strong>
            <p>
              Job Tracker's use and transfer to any other app of information received from Google APIs adheres to the 
              <a href="https://developers.google.com/terms/api-services-user-data-policy" target="_blank" rel="noopener">
                Google API Services User Data Policy
              </a>, including the Limited Use requirements.
            </p>
          </div>

          <h3>Our Commitments:</h3>
          <ul>
            <li><strong>Strict Purpose Limitation:</strong> Email data is accessed solely to identify job application updates (applied, interviewing, rejected, offered).</li>
            <li><strong>No Transfer for Advertising:</strong> We do not transfer Google user data to serve ads, including personalized or targeted advertising.</li>
            <li><strong>No Transfer to Data Brokers:</strong> Data is never sold or provided to data brokers or information resellers.</li>
            <li><strong>No AI Model Training:</strong> Email contents are never used to train or improve generalized AI/ML models.</li>
            <li><strong>Human Access Safeguards:</strong> No human reads your email messages unless you have given affirmative agreement for specific debugging or as required by law.</li>
          </ul>
        </section>
      </div>

      <footer class="legal-footer">
        <p>&copy; 2026 Job Tracker. Distributed under the MIT License.</p>
      </footer>
    </div>
  `,
  styles: [`
    .legal-page {
      min-height: 100vh;
      background: #0f172a;
      color: #e2e8f0;
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
      line-height: 1.6;
    }
    .legal-header {
      background: #1e293b;
      border-bottom: 1px solid #334155;
      padding: 40px 20px;
    }
    .header-content {
      max-width: 900px;
      margin: 0 auto;
    }
    .back-link {
      color: #38bdf8;
      text-decoration: none;
      font-size: 14px;
      display: inline-block;
      margin-bottom: 12px;
      font-weight: 500;
    }
    .back-link:hover {
      text-decoration: underline;
    }
    h1 {
      font-size: 28px;
      font-weight: 700;
      color: #f8fafc;
      margin: 0 0 8px 0;
    }
    .subtitle {
      color: #94a3b8;
      font-size: 16px;
      margin: 0;
    }
    .legal-container {
      max-width: 900px;
      margin: 30px auto;
      padding: 0 20px;
    }
    .legal-tabs {
      display: flex;
      gap: 10px;
      border-bottom: 1px solid #334155;
      padding-bottom: 12px;
      margin-bottom: 24px;
      flex-wrap: wrap;
    }
    .tab-button {
      background: transparent;
      border: 1px solid #334155;
      color: #94a3b8;
      padding: 8px 16px;
      border-radius: 6px;
      cursor: pointer;
      font-size: 14px;
      transition: all 0.2s;
    }
    .tab-button:hover {
      background: #1e293b;
      color: #f8fafc;
    }
    .tab-button.active {
      background: #38bdf8;
      color: #0f172a;
      font-weight: 600;
      border-color: #38bdf8;
    }
    .legal-content {
      background: #1e293b;
      border-radius: 8px;
      padding: 32px;
      border: 1px solid #334155;
    }
    .legal-content h2 {
      font-size: 24px;
      color: #f8fafc;
      margin-top: 0;
    }
    .legal-content h3 {
      font-size: 18px;
      color: #38bdf8;
      margin-top: 24px;
    }
    .last-updated {
      color: #64748b;
      font-size: 13px;
      margin-bottom: 20px;
    }
    .callout-box {
      background: rgba(56, 189, 248, 0.1);
      border-left: 4px solid #38bdf8;
      padding: 16px;
      border-radius: 4px;
      margin: 20px 0;
    }
    .callout-box p {
      margin: 8px 0 0 0;
    }
    .policy-table {
      width: 100%;
      border-collapse: collapse;
      margin-top: 16px;
      font-size: 14px;
    }
    .policy-table th, .policy-table td {
      border: 1px solid #334155;
      padding: 10px 12px;
      text-align: left;
    }
    .policy-table th {
      background: #0f172a;
      color: #cbd5e1;
    }
    code {
      background: #0f172a;
      color: #38bdf8;
      padding: 2px 6px;
      border-radius: 4px;
      font-size: 13px;
    }
    a {
      color: #38bdf8;
    }
    .legal-footer {
      text-align: center;
      padding: 40px 20px;
      color: #64748b;
      font-size: 13px;
    }
    .mt-4 { margin-top: 16px; }
  `]
})
export class LegalComponent implements OnInit {
  activeTab: 'privacy' | 'terms' | 'cookies' | 'google' = 'privacy';

  constructor(private route: ActivatedRoute) {}

  ngOnInit(): void {
    this.route.url.subscribe(segments => {
      const path = segments[0]?.path;
      if (path === 'terms') {
        this.activeTab = 'terms';
      } else if (path === 'cookie-policy') {
        this.activeTab = 'cookies';
      } else if (path === 'google-limited-use') {
        this.activeTab = 'google';
      } else {
        this.activeTab = 'privacy';
      }
    });
  }

  setTab(tab: 'privacy' | 'terms' | 'cookies' | 'google') {
    this.activeTab = tab;
  }
}
