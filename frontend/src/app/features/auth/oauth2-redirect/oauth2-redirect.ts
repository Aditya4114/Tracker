import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../../core/auth.service';
import { inject } from '@angular/core';

@Component({
  selector: 'app-oauth2-redirect',
  standalone: true,
  template: `<div style="display:flex;justify-content:center;align-items:center;height:100vh;"><h2>Authenticating...</h2></div>`
})
export class OAuth2RedirectHandler implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private authService = inject(AuthService);

  ngOnInit() {
    this.route.queryParams.subscribe(params => {
      const token = params['token'];
      if (token) {
        this.authService.setToken(token);
        this.router.navigate(['/dashboard']);
      } else {
        this.router.navigate(['/login']);
      }
    });
  }
}
