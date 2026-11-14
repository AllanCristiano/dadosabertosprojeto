import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth';

@Component({
  selector: 'app-login',
  imports: [FormsModule],
  template: `
    <section class="wrap">
      <h1 aria-label="Pulso dos Combustíveis">
        @for (w of words; track $index) {
          <span class="w" [style.--i]="$index">{{ w }}</span>
        }
      </h1>
      <p class="sub">Onde o preço subiu acima da média nacional? A resposta, em tempo real, no seu bolso.</p>

      <form class="glass card" (ngSubmit)="submit()">
        <label class="field">Usuário<input name="u" [(ngModel)]="user" autocomplete="username" required /></label>
        <label class="field">Senha<input name="p" type="password" [(ngModel)]="pass" autocomplete="current-password" required /></label>
        @if (error()) { <p class="err" role="alert">{{ error() }}</p> }
        <button class="btn" [disabled]="loading()">{{ loading() ? 'Entrando…' : 'Entrar' }}</button>
        <small>Demo: <code>demo</code> / <code>demo</code></small>
      </form>
    </section>
  `,
  styles: `
    .wrap { min-height: 100dvh; display: grid; align-content: center; gap: 22px; padding: 28px 20px; max-width: 460px; margin: 0 auto; }
    h1 { font-size: clamp(2.6rem, 14vw, 4.2rem); line-height: .95; font-weight: 800; letter-spacing: -.03em; display: grid; }
    .w { display: block; background: linear-gradient(120deg, #fff 30%, var(--accent)); -webkit-background-clip: text; background-clip: text; color: transparent;
      opacity: 0; transform: translateY(60%) rotateX(-70deg); transform-origin: 0 100%; filter: blur(10px);
      animation: word 1.1s var(--ease-out) calc(var(--i) * 160ms + 150ms) forwards; }
    @keyframes word { to { opacity: 1; transform: none; filter: none; } }
    .sub { color: var(--muted); animation: fade 1.2s var(--ease-out) .7s both; }
    @keyframes fade { from { opacity: 0; transform: translateY(14px); } }
    .card { padding: 22px; display: grid; gap: 16px; animation: fade 1.2s var(--ease-out) .95s both; }
    .err { color: var(--up); font-size: .9rem; animation: shake .45s; }
    @keyframes shake { 25% { transform: translateX(-6px); } 75% { transform: translateX(6px); } }
    small { color: var(--muted); text-align: center; } code { color: var(--accent); }
  `,
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly words = ['Pulso', 'dos', 'Combustíveis'];
  protected user = 'demo';
  protected pass = 'demo';
  protected readonly loading = signal(false);
  protected readonly error = signal('');

  submit() {
    this.loading.set(true);
    this.error.set('');
    this.auth.login(this.user, this.pass).subscribe({
      next: () => this.router.navigateByUrl('/painel'),
      error: (e) => {
        this.loading.set(false);
        this.error.set(e.status === 401 ? 'Usuário ou senha inválidos.' : 'API indisponível. Tente novamente.');
      },
    });
  }
}
