import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe, DecimalPipe } from '@angular/common';
import { ApiService } from '../../core/api.service';
import { Page, Preco } from '../../core/models';
import { downloadCsv } from '../../shared/csv';
import { RevealDirective } from '../../shared/motion';

@Component({
  selector: 'app-explorer',
  imports: [FormsModule, DatePipe, DecimalPipe, RevealDirective],
  template: `
    <section appReveal>
      <p class="kicker">Explorar</p>
      <h2>Cada coleta de preço, <em>do posto ao dado.</em></h2>
    </section>

    <form class="glass filters" appReveal [delay]="80" (ngSubmit)="buscar(0)">
      <label class="field">Produto
        <select name="produto" [(ngModel)]="produto"><option value="">Todos</option>@for (p of produtos(); track p) { <option>{{ p }}</option> }</select>
      </label>
      <label class="field">UF
        <select name="uf" [(ngModel)]="uf"><option value="">Todas</option>@for (u of ufs(); track u) { <option>{{ u }}</option> }</select>
      </label>
      <label class="field">De<input type="date" name="de" [(ngModel)]="de" /></label>
      <label class="field">Até<input type="date" name="ate" [(ngModel)]="ate" /></label>
      <button class="btn">Filtrar</button>
      <button class="btn ghost" type="button" (click)="exportar()" [disabled]="!data()?.content?.length">CSV</button>
    </form>

    @if (data(); as d) {
      <p class="count" appReveal>{{ d.totalElements | number: '1.0-0' : 'pt-BR' }} coletas</p>
      <ul class="list">
        @for (p of d.content; track $index; let i = $index) {
          <li class="glass" [style.--i]="i">
            <div><strong>{{ p.revenda || 'Revenda' }}</strong><small>{{ p.municipio }} · {{ p.uf }} · {{ p.bandeira || '—' }}</small></div>
            <div class="r"><b>R$ {{ p.precoVenda | number: '1.2-3' : 'pt-BR' }}</b><small>{{ p.data | date: 'dd/MM/yy' }} · {{ p.produto }}</small></div>
          </li>
        }
      </ul>
      <nav class="pager">
        <button class="btn ghost" (click)="buscar(d.page - 1)" [disabled]="d.page === 0">← Anterior</button>
        <span>{{ d.page + 1 }} / {{ d.totalPages || 1 }}</span>
        <button class="btn ghost" (click)="buscar(d.page + 1)" [disabled]="d.page + 1 >= d.totalPages">Próxima →</button>
      </nav>
    } @else if (loading()) { <div class="sk glass"></div> }
  `,
  styles: `
    :host { display: grid; gap: 16px; padding-top: 8px; }
    .kicker { color: var(--accent); font-size: .75rem; letter-spacing: .14em; text-transform: uppercase; }
    h2 { font-size: clamp(1.6rem, 6vw, 2.4rem); font-weight: 800; letter-spacing: -.02em; line-height: 1.08; margin-top: 6px; }
    h2 em { font-style: normal; color: var(--accent); }
    .filters { padding: 16px; display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
    .filters .btn { grid-column: span 1; }
    .count { color: var(--muted); font-size: .85rem; }
    .list { list-style: none; margin: 0; padding: 0; display: grid; gap: 10px; }
    .list li { padding: 14px 16px; display: flex; justify-content: space-between; gap: 12px; animation: in .7s var(--ease-out) calc(var(--i) * 40ms) both; }
    @keyframes in { from { opacity: 0; transform: translateY(24px) scale(.97); filter: blur(4px); } }
    .list strong { display: block; font-size: .95rem; } small { color: var(--muted); font-size: .75rem; display: block; }
    .r { text-align: right; flex: none; } .r b { color: var(--accent); font-size: 1.1rem; }
    .pager { display: flex; justify-content: space-between; align-items: center; color: var(--muted); }
    .pager .btn { min-height: 42px; padding: 0 16px; } .pager .btn:disabled { opacity: .35; }
    .sk { height: 300px; background: linear-gradient(100deg, var(--glass) 30%, var(--glass-strong) 50%, var(--glass) 70%) 0 0 / 200% 100%; animation: sh 1.4s infinite linear; }
    @keyframes sh { to { background-position: -200% 0; } }
    @media (min-width: 760px) { .filters { grid-template-columns: repeat(4, 1fr) auto auto; align-items: end; } .list { grid-template-columns: 1fr 1fr; } }
  `,
})
export class ExplorerPage {
  private readonly api = inject(ApiService);
  protected readonly produtos = signal<string[]>([]);
  protected readonly ufs = signal<string[]>([]);
  protected readonly data = signal<Page<Preco> | null>(null);
  protected readonly loading = signal(true);
  protected produto = '';
  protected uf = '';
  protected de = '';
  protected ate = '';

  constructor() {
    this.api.produtos().subscribe((p) => this.produtos.set(p));
    this.api.ufs().subscribe((u) => this.ufs.set(u));
    this.buscar(0);
  }

  protected buscar(page: number) {
    this.loading.set(true);
    this.api.precos({ produto: this.produto, uf: this.uf, de: this.de, ate: this.ate, page, size: 20 }).subscribe({
      next: (d) => { this.data.set(d); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  protected exportar() {
    this.api.precos({ produto: this.produto, uf: this.uf, de: this.de, ate: this.ate, page: 0, size: 200 }).subscribe((d) =>
      downloadCsv('coletas.csv', d.content.map((p) => ({ data: p.data, produto: p.produto, uf: p.uf, municipio: p.municipio, revenda: p.revenda, bandeira: p.bandeira, preco_venda: p.precoVenda, preco_compra: p.precoCompra }))));
  }
}
