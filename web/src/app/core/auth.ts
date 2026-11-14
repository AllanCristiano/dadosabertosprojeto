import { HttpInterceptorFn } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { tap, catchError, throwError } from 'rxjs';
import { ApiService } from './api.service';

const KEY = 'fuel_token';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  readonly token = signal<string | null>(this.read());

  private read() {
    try { return sessionStorage.getItem(KEY); } catch { return null; }
  }
  login(u: string, p: string) {
    return this.api.login(u, p).pipe(
      tap((r) => {
        this.token.set(r.accessToken);
        try { sessionStorage.setItem(KEY, r.accessToken); } catch { /* storage indisponível */ }
      }),
    );
  }
  logout() {
    this.token.set(null);
    try { sessionStorage.removeItem(KEY); } catch { /* noop */ }
    this.router.navigateByUrl('/login');
  }
}

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const t = auth.token();
  const authed = t && !req.url.includes('/auth/') ? req.clone({ setHeaders: { Authorization: `Bearer ${t}` } }) : req;
  return next(authed).pipe(
    catchError((e) => {
      if (e.status === 401 && !req.url.includes('/auth/')) auth.logout();
      return throwError(() => e);
    }),
  );
};

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.token() ? true : inject(Router).parseUrl('/login');
};
