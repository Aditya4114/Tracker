import { Routes } from '@angular/router';
import { Login } from './features/auth/login/login';
import { Register } from './features/auth/register/register';
import { Dashboard } from './features/dashboard/dashboard';

import { OAuth2RedirectHandler } from './features/auth/oauth2-redirect/oauth2-redirect';
import { LegalComponent } from './features/legal/legal.component';

export const routes: Routes = [
    { path: '', redirectTo: '/login', pathMatch: 'full' },
    { path: 'login', component: Login },
    { path: 'register', component: Register },
    { path: 'dashboard', component: Dashboard },
    { path: 'oauth2/redirect', component: OAuth2RedirectHandler },
    { path: 'privacy', component: LegalComponent },
    { path: 'terms', component: LegalComponent },
    { path: 'cookie-policy', component: LegalComponent },
    { path: 'google-limited-use', component: LegalComponent }
];
