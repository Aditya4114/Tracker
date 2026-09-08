import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-cookie-banner',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
  template: `
    <!-- Bottom Cookie Banner -->
    <div *ngIf="showBanner" class="cookie-banner-container fade-in">
      <div class="cookie-banner-content">
        <div class="cookie-icon">🍪</div>
        <div class="cookie-text">
          <strong>Privacy & Cookie Consent</strong>
          <p>
            We use strictly necessary browser storage (JWT session tokens) to make Job Tracker work. 
            We do not use advertising trackers. By continuing, you consent to our use of essential storage in accordance with our 
            <a routerLink="/privacy" target="_blank">Privacy Policy</a> and 
            <a routerLink="/cookie-policy" target="_blank">Cookie Policy</a>.
          </p>
        </div>
        <div class="cookie-actions">
          <button class="btn-cookie-outline" (click)="openModal()">Settings</button>
          <button class="btn-cookie-secondary" (click)="acceptEssential()">Essential Only</button>
          <button class="btn-cookie-primary" (click)="acceptAll()">Accept All</button>
        </div>
      </div>
    </div>

    <!-- Cookie Settings Modal -->
    <div *ngIf="showModal" class="cookie-modal-backdrop fade-in">
      <div class="cookie-modal-card">
        <div class="modal-header">
          <h3>Cookie & Storage Preferences</h3>
          <button class="close-btn" (click)="closeModal()">&times;</button>
        </div>

        <div class="modal-body">
          <p class="modal-description">
            Customize which browser storage items you permit Job Tracker to use. Essential items cannot be disabled as they are required for security and core application functionality.
          </p>

          <!-- Category 1: Strictly Necessary -->
          <div class="preference-group">
            <div class="preference-header">
              <div>
                <strong>Strictly Necessary Storage</strong>
                <p>Required for secure authentication, session management, and API calls.</p>
              </div>
              <span class="badge-always-active">Always Active</span>
            </div>
            <div class="preference-details">
              Items: <code>auth-token</code> (JWT session), <code>username</code>, <code>cookie-consent</code>.
            </div>
          </div>

          <!-- Category 2: Functional / Preferences -->
          <div class="preference-group">
            <div class="preference-header">
              <div>
                <strong>Functional & Display Preferences</strong>
                <p>Remembers custom date sync filters and UI view states across sessions.</p>
              </div>
              <label class="switch">
                <input type="checkbox" [(ngModel)]="functionalEnabled">
                <span class="slider round"></span>
              </label>
            </div>
            <div class="preference-details">
              Items: <code>job_tracker_sync_range</code>, <code>theme-preference</code>.
            </div>
          </div>

          <!-- Third Party Notice -->
          <div class="third-party-note">
            <span>🛡️</span>
            <small>We do NOT use third-party marketing, analytics, or advertising cookies.</small>
          </div>
        </div>

        <div class="modal-footer">
          <button class="btn-modal-secondary" (click)="acceptEssential()">Reject Non-Essential</button>
          <button class="btn-modal-primary" (click)="savePreferences()">Save Preferences</button>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .cookie-banner-container {
      position: fixed;
      bottom: 0;
      left: 0;
      right: 0;
      background: rgba(15, 23, 42, 0.95);
      backdrop-filter: blur(12px);
      border-top: 1px solid #334155;
      padding: 16px 24px;
      z-index: 9999;
      box-shadow: 0 -4px 20px rgba(0, 0, 0, 0.4);
    }
    .cookie-banner-content {
      max-width: 1200px;
      margin: 0 auto;
      display: flex;
      align-items: center;
      gap: 20px;
      flex-wrap: wrap;
    }
    .cookie-icon {
      font-size: 28px;
    }
    .cookie-text {
      flex: 1;
      min-width: 280px;
      color: #cbd5e1;
      font-size: 13.5px;
      line-height: 1.5;
    }
    .cookie-text strong {
      color: #f8fafc;
      font-size: 14px;
      display: block;
      margin-bottom: 2px;
    }
    .cookie-text a {
      color: #38bdf8;
      text-decoration: underline;
    }
    .cookie-actions {
      display: flex;
      gap: 10px;
      align-items: center;
      flex-wrap: wrap;
    }
    button {
      font-family: inherit;
      cursor: pointer;
      font-size: 13px;
      font-weight: 500;
      padding: 8px 16px;
      border-radius: 6px;
      transition: all 0.2s;
    }
    .btn-cookie-primary {
      background: #38bdf8;
      color: #0f172a;
      border: 1px solid #38bdf8;
      font-weight: 600;
    }
    .btn-cookie-primary:hover {
      background: #7dd3fc;
    }
    .btn-cookie-secondary {
      background: #1e293b;
      color: #e2e8f0;
      border: 1px solid #475569;
    }
    .btn-cookie-secondary:hover {
      background: #334155;
    }
    .btn-cookie-outline {
      background: transparent;
      color: #94a3b8;
      border: 1px solid #334155;
    }
    .btn-cookie-outline:hover {
      color: #f8fafc;
      border-color: #64748b;
    }
    
    /* Modal Styles */
    .cookie-modal-backdrop {
      position: fixed;
      inset: 0;
      background: rgba(0, 0, 0, 0.7);
      backdrop-filter: blur(4px);
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 20px;
      z-index: 10000;
    }
    .cookie-modal-card {
      background: #1e293b;
      border: 1px solid #334155;
      border-radius: 10px;
      max-width: 540px;
      width: 100%;
      color: #e2e8f0;
      box-shadow: 0 10px 30px rgba(0, 0, 0, 0.5);
    }
    .modal-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 18px 24px;
      border-bottom: 1px solid #334155;
    }
    .modal-header h3 {
      margin: 0;
      font-size: 18px;
      color: #f8fafc;
    }
    .close-btn {
      background: transparent;
      border: none;
      color: #94a3b8;
      font-size: 24px;
      padding: 0;
      line-height: 1;
    }
    .modal-body {
      padding: 20px 24px;
    }
    .modal-description {
      font-size: 13.5px;
      color: #94a3b8;
      margin-top: 0;
      margin-bottom: 18px;
      line-height: 1.5;
    }
    .preference-group {
      background: #0f172a;
      border: 1px solid #334155;
      border-radius: 8px;
      padding: 14px 16px;
      margin-bottom: 14px;
    }
    .preference-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 12px;
    }
    .preference-header strong {
      display: block;
      color: #f8fafc;
      font-size: 14px;
    }
    .preference-header p {
      margin: 4px 0 0 0;
      font-size: 12.5px;
      color: #94a3b8;
    }
    .badge-always-active {
      background: #0369a1;
      color: #e0f2fe;
      font-size: 11px;
      font-weight: 600;
      padding: 4px 8px;
      border-radius: 4px;
      white-space: nowrap;
    }
    .preference-details {
      font-size: 12px;
      color: #64748b;
      margin-top: 8px;
      border-top: 1px solid #1e293b;
      padding-top: 8px;
    }
    code {
      background: #1e293b;
      color: #38bdf8;
      padding: 1px 4px;
      border-radius: 3px;
    }
    .third-party-note {
      display: flex;
      align-items: center;
      gap: 8px;
      color: #10b981;
      font-size: 12px;
      margin-top: 12px;
    }
    .modal-footer {
      display: flex;
      justify-content: flex-end;
      gap: 12px;
      padding: 16px 24px;
      border-top: 1px solid #334155;
    }
    .btn-modal-primary {
      background: #38bdf8;
      color: #0f172a;
      border: 1px solid #38bdf8;
      font-weight: 600;
    }
    .btn-modal-secondary {
      background: transparent;
      color: #cbd5e1;
      border: 1px solid #475569;
    }

    /* Toggle Switch */
    .switch {
      position: relative;
      display: inline-block;
      width: 44px;
      height: 24px;
      flex-shrink: 0;
    }
    .switch input { opacity: 0; width: 0; height: 0; }
    .slider {
      position: absolute;
      cursor: pointer;
      inset: 0;
      background-color: #334155;
      transition: .3s;
      border-radius: 24px;
    }
    .slider:before {
      position: absolute;
      content: "";
      height: 18px;
      width: 18px;
      left: 3px;
      bottom: 3px;
      background-color: white;
      transition: .3s;
      border-radius: 50%;
    }
    input:checked + .slider {
      background-color: #38bdf8;
    }
    input:checked + .slider:before {
      transform: translateX(20px);
    }
    .fade-in {
      animation: fadeIn 0.25s ease-in;
    }
    @keyframes fadeIn {
      from { opacity: 0; }
      to { opacity: 1; }
    }
  `]
})
export class CookieBannerComponent implements OnInit {
  showBanner = false;
  showModal = false;
  functionalEnabled = true;

  ngOnInit(): void {
    const consent = localStorage.getItem('cookie-consent');
    if (!consent) {
      this.showBanner = true;
    } else if (consent === 'essential-only') {
      this.functionalEnabled = false;
    }
  }

  acceptAll(): void {
    localStorage.setItem('cookie-consent', 'accepted');
    this.functionalEnabled = true;
    this.showBanner = false;
    this.showModal = false;
  }

  acceptEssential(): void {
    localStorage.setItem('cookie-consent', 'essential-only');
    this.functionalEnabled = false;
    this.showBanner = false;
    this.showModal = false;
  }

  savePreferences(): void {
    if (this.functionalEnabled) {
      this.acceptAll();
    } else {
      this.acceptEssential();
    }
  }

  openModal(): void {
    this.showModal = true;
  }

  closeModal(): void {
    this.showModal = false;
  }
}
