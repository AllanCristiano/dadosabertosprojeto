import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <header class="top">
      <span class="brand"><span class="dot"></span>Pulso<b>Combustíveis</b></span>
      <button class="out" (click)="auth.logout()" aria-label="Sair">Sair</button>
    </header>
    <main><router-outlet /></main>
    <nav class="tabs glass" aria-label="Navegação">
      <a routerLink="/painel" routerLinkActive="on"><svg viewBox="0 0 24 24"><path d="M4 20V10m6 10V4m6 16v-7m4 7H2"/></svg><span>Painel</span></a>
      <a routerLink="/explorar" routerLinkActive="on"><svg viewBox="0 0 24 24"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg><span>Explorar</span></a>
    </nav>
  `,
  styles: `
    :host { display: block; }
    .top { position: sticky; top: 0; z-index: 20; display: flex; justify-content: space-between; align-items: center;
      padding: calc(12px + env(safe-area-inset-top, 0px)) 18px 12px; backdrop-filter: blur(14px); background: linear-gradient(#07080fcc, #07080f00); }
    .brand { font-weight: 300; letter-spacing: .02em; display: flex; align-items: center; gap: 10px; }
    .brand b { font-weight: 800; margin-left: 4px; }
    .dot { width: 10px; height: 10px; border-radius: 50%; background: var(--accent); box-shadow: 0 0 0 0 #ffb02099; animation: pulse 2.2s infinite; }
    @keyframes pulse { 70% { box-shadow: 0 0 0 12px #ffb02000; } 100% { box-shadow: 0 0 0 0 #ffb02000; } }
    .out { background: none; border: 1px solid var(--border); border-radius: 999px; padding: 6px 14px; color: var(--muted); cursor: pointer; }
    main { padding: 4px 16px calc(var(--nav-h) + var(--safe-b) + 28px); max-width: 1100px; margin: 0 auto; }
    .tabs { position: fixed; z-index: 30; left: 50%; bottom: calc(12px + var(--safe-b)); transform: translateX(-50%);
      display: flex; gap: 4px; padding: 6px; border-radius: 999px; width: min(92vw, 360px); animation: rise .9s var(--ease-out) .3s both; }
    @keyframes rise { from { transform: translate(-50%, 140%); opacity: 0; } }
    .tabs a { flex: 1; display: grid; justify-items: center; gap: 2px; padding: 8px 0; border-radius: 999px; color: var(--muted);
      text-decoration: none; font-size: .72rem; transition: background .4s var(--ease-out), color .4s, transform .3s var(--ease-out); }
    .tabs a:active { transform: scale(.94); }
    .tabs a.on { background: var(--glass-strong); color: var(--accent); }
    svg { width: 22px; height: 22px; fill: none; stroke: currentColor; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; }
    @media (min-width: 900px) { main { padding-top: 12px; } }
  `,
})
export class Shell {
  protected readonly auth = inject(AuthService);
}
