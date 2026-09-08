import { Component } from '@angular/core';
import { RouterOutlet, RouterModule } from '@angular/router';
import { CookieBannerComponent } from './shared/cookie-banner/cookie-banner.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterModule, CookieBannerComponent],
  template: `
    <main class="app-container">
      <router-outlet></router-outlet>
      <app-cookie-banner #cookieBanner></app-cookie-banner>
      
      <footer class="app-footer">
        <div class="footer-links">
          <a routerLink="/privacy">Privacy Policy</a>
          <span class="sep">•</span>
          <a routerLink="/terms">Terms of Service</a>
          <span class="sep">•</span>
          <a routerLink="/cookie-policy">Cookie Policy</a>
          <span class="sep">•</span>
          <a routerLink="/google-limited-use">Google Limited Use</a>
          <span class="sep">•</span>
          <button type="button" class="link-btn" (click)="cookieBanner.openModal()">Cookie Settings</button>
        </div>
      </footer>
    </main>
  `,
  styles: [`
    .app-container {
      width: 100%;
      min-height: 100vh;
      display: flex;
      flex-direction: column;
    }
    .app-footer {
      margin-top: auto;
      padding: 14px 20px;
      background: rgba(15, 23, 42, 0.85);
      border-top: 1px solid rgba(51, 65, 85, 0.5);
      text-align: center;
      font-size: 12px;
      color: #64748b;
      z-index: 10;
    }
    .footer-links {
      display: flex;
      justify-content: center;
      align-items: center;
      gap: 12px;
      flex-wrap: wrap;
    }
    .footer-links a, .link-btn {
      color: #94a3b8;
      text-decoration: none;
      transition: color 0.2s;
      background: transparent;
      border: none;
      padding: 0;
      font-family: inherit;
      font-size: inherit;
      cursor: pointer;
    }
    .footer-links a:hover, .link-btn:hover {
      color: #38bdf8;
      text-decoration: underline;
    }
    .sep {
      color: #334155;
    }
  `]
})
export class App {}
