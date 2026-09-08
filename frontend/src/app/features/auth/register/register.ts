import { Component } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { inject } from '@angular/core';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './register.html',
  styleUrls: ['../login/login.css'] // reuse login styles
})
export class Register {
  private http = inject(HttpClient);
  private router = inject(Router);

  username = '';
  email = '';
  password = '';
  agreeConsent = false;
  loading = false;

  onSubmit() {
    if (!this.username || !this.email || !this.password) return;
    if (!this.agreeConsent) {
      alert('Please agree to the Terms of Service, Privacy Policy, and Data Usage Consent to create an account.');
      return;
    }
    this.loading = true;
    this.http.post('http://localhost:8080/api/auth/register', { 
      username: this.username, 
      email: this.email, 
      password: this.password 
    }, { responseType: 'text' }).subscribe({
      next: () => {
        this.loading = false;
        alert('Registration successful! Please login.');
        this.router.navigate(['/login']);
      },
      error: (err) => {
        this.loading = false;
        alert('Registration failed: ' + (err.error || 'Unknown error'));
      }
    });
  }

  signUpWithGoogle() {
    window.location.href = 'http://localhost:8080/oauth2/authorization/google';
  }
}
