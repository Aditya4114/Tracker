import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable, tap, BehaviorSubject } from 'rxjs';

const API_URL = 'http://localhost:8080/api/auth/';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private loggedIn = new BehaviorSubject<boolean>(this.hasToken());
  private http = inject(HttpClient);

  login(username: string, password: string): Observable<any> {
    return this.http.post(API_URL + 'login', { username, password }).pipe(
      tap((res: any) => {
        if (res.token) {
          localStorage.setItem('auth-token', res.token);
          localStorage.setItem('username', res.username);
          this.loggedIn.next(true);
        }
      })
    );
  }

  logout(): void {
    localStorage.removeItem('auth-token');
    localStorage.removeItem('username');
    this.loggedIn.next(false);
  }

  isLoggedIn(): Observable<boolean> {
    return this.loggedIn.asObservable();
  }

  private hasToken(): boolean {
    return !!localStorage.getItem('auth-token');
  }

  getToken(): string | null {
    return localStorage.getItem('auth-token');
  }

  setToken(token: string): void {
    localStorage.setItem('auth-token', token);
    this.loggedIn.next(true);
  }
}
