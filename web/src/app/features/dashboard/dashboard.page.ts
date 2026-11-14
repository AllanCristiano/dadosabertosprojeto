import { Component, computed, inject, signal } from '@angular/core';
import { ChartConfiguration } from 'chart.js';
import { ApiService } from '../../core/api.service';
import { Analise } from '../../core/models';
import { ChartComponent } from '../../shared/chart.component';
import { downloadCsv } from '../../shared/csv';
import { CountUpDirective, RevealDirective } from '../../shared/motion';

@Component({
  selector: 'app-dashboard',
  imports: [ChartComponent, CountUpDirective, RevealDirective],
  template: `
    <section appReveal>
      <p class="kicker">Pergunta de negócio</p>
      <h2>Onde o preço subiu <em>acima da média nacional?</em></h2>
    </section>

    <div class="chips" appReveal [delay]="80" role="tablist">
      @for (p of produtos(); track p) {
        <button role="tab" [class.on]="p === produto()" (click)="setProduto(p)">{{ p }}</button>
      }
    </div>

    @if (ranking().length) {
      <div class="kpis">
        <article class="glass kpi" appReveal>
          <span>Maior alta</span>
          <strong class="up"><span [appCountUp]="top().variacaoPct" [decimals]="1" prefix="+" suffix="%"></span></strong>
          <small>{{ top().uf }}</small>
        </article>
        <article class="glass kpi" appReveal [delay]="100">
          <span>UFs acima</span>
          <strong><span [appCountUp]="acima()"></span><i>/{{ ranking().length }}</i></strong>
          <small>da média nacional</small>
        </article>
        <article class="glass kpi" appReveal [delay]="200">
          <span>Média nacional</span>
          <strong><span [appCountUp]="top().precoMedioNacional" [decimals]="2" prefix="R$ "></span></strong>
          <small>{{ mesLabel() }}</small>
        </article>
      </div>

      <article class="glass panel" appReveal>
        <header>
          <h3>Variação vs. média nacional</h3>
          <button class="btn ghost sm" (click)="exportar()">Exportar CSV</button>
        </header>
        <ul class="bars">
          @for (r of ranking(); track r.uf; let i = $index) {
            <li [class.sel]="r.uf === uf()" (click)="setUf(r.uf)" [style.--i]="i" [style.--w]="barW(r)" [class.neg]="r.variacaoPct < 0">
              <b>{{ r.uf }}</b>
              <span class="track"><span class="fill"></span></span>
              <em>{{ r.variacaoPct > 0 ? '+' : '' }}{{ r.variacaoPct.toFixed(1) }}%</em>
            </li>
          }
        </ul>
        <p class="hint">Toque em uma UF para ver a evolução mensal.</p>
      </article>

      <article class="glass panel" appReveal>
        <header><h3>{{ uf() }} × Brasil <small>preço médio mensal</small></h3></header>
        <div class="chart"><app-chart [config]="lineCfg()" /></div>
      </article>
    } @else if (loading()) {
      <div class="skeleton glass"></div>
    } @else {
      <p class="glass empty">Sem dados ainda. Rode a DAG <code>anp_combustiveis_etl</code> no Airflow.</p>
    }
  `,
  styles: `
    :host { display: grid; gap: 18px; padding-top: 8px; }
    .kicker { color: var(--accent); font-size: .75rem; letter-spacing: .14em; text-transform: uppercase; }
    h2 { font-size: clamp(1.7rem, 7vw, 2.8rem); font-weight: 800; letter-spacing: -.02em; line-height: 1.05; margin-top: 6px; }
    h2 em { font-style: normal; background: linear-gradient(120deg, var(--accent), var(--accent-2)); -webkit-background-clip: text; background-clip: text; color: transparent; }
    .chips { display: flex; gap: 8px; overflow-x: auto; scrollbar-width: none; margin: 0 -16px; padding: 4px 16px; scroll-snap-type: x proximity; }
    .chips button { flex: none; scroll-snap-align: start; padding: 10px 16px; border-radius: 999px; border: 1px solid var(--border); background: var(--glass); cursor: pointer; font-size: .8rem;
      transition: all .45s var(--ease-out); white-space: nowrap; }
    .chips button.on { background: var(--accent); color: #1a1100; border-color: transparent; transform: scale(1.05); font-weight: 600; }
    .kpis { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
    .kpi { padding: 16px; display: grid; gap: 4px; }
    .kpi:first-child { grid-column: span 2; }
    .kpi > span:first-child { color: var(--muted); font-size: .72rem; text-transform: uppercase; letter-spacing: .1em; }
    .kpi strong { font-size: 2rem; font-weight: 800; letter-spacing: -.02em; } .kpi strong i { font-style: normal; color: var(--muted); font-size: 1rem; font-weight: 300; }
    .kpi small { color: var(--muted); } .up { color: var(--up); }
    .panel { padding: 18px 16px; display: grid; gap: 14px; }
    .panel header { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
    h3 { font-size: 1rem; font-weight: 600; } h3 small { color: var(--muted); font-weight: 300; margin-left: 6px; }
    .sm { min-height: 36px; padding: 0 14px; font-size: .8rem; }
    .bars { list-style: none; margin: 0; padding: 0; display: grid; gap: 6px; }
    .bars li { display: grid; grid-template-columns: 30px 1fr 62px; align-items: center; gap: 10px; padding: 6px 8px; border-radius: 12px; cursor: pointer; transition: background .3s; }
    .bars li.sel { background: var(--glass-strong); }
    .bars b { font-weight: 600; font-size: .85rem; } .bars em { font-style: normal; text-align: right; font-size: .85rem; color: var(--up); font-variant-numeric: tabular-nums; }
    .bars li.neg em { color: var(--down); }
    .track { height: 10px; border-radius: 99px; background: rgba(255,255,255,.06); overflow: hidden; }
    .fill { display: block; height: 100%; width: 100%; border-radius: inherit; transform-origin: left; transform: scaleX(0);
      background: linear-gradient(90deg, var(--accent), var(--accent-2)); animation: grow 1.2s var(--ease-out) calc(var(--i) * 35ms + 200ms) forwards; --s: var(--w); }
    .neg .fill { background: linear-gradient(90deg, #00c2a8, var(--down)); }
    @keyframes grow { to { transform: scaleX(var(--w)); } }
    .hint { color: var(--muted); font-size: .78rem; }
    .chart { height: 260px; }
    .skeleton { height: 280px; background: linear-gradient(100deg, var(--glass) 30%, var(--glass-strong) 50%, var(--glass) 70%) 0 0 / 200% 100%; animation: sh 1.4s infinite linear; }
    @keyframes sh { to { background-position: -200% 0; } }
    .empty { padding: 24px; color: var(--muted); } code { color: var(--accent); }
    @media (min-width: 760px) {
      .kpis { grid-template-columns: repeat(3, 1fr); } .kpi:first-child { grid-column: auto; }
      .chart { height: 340px; }
    }
  `,
})
export class DashboardPage {
  private readonly api = inject(ApiService);
  protected readonly produtos = signal<string[]>([]);
  protected readonly produto = signal('GASOLINA');
  protected readonly uf = signal('SP');
  protected readonly ranking = signal<Analise[]>([]);
  protected readonly serie = signal<Analise[]>([]);
  protected readonly loading = signal(true);

  protected readonly top = computed(() => this.ranking()[0]);
  protected readonly acima = computed(() => this.ranking().filter((r) => r.acimaDaMedia).length);
  protected readonly mesLabel = computed(() => this.ranking()[0]?.mesRef.slice(0, 7).split('-').reverse().join('/') ?? '');
  private readonly maxAbs = computed(() => Math.max(1, ...this.ranking().map((r) => Math.abs(r.variacaoPct))));

  protected readonly lineCfg = computed<ChartConfiguration>(() => {
    const s = this.serie();
    return {
      type: 'line',
      data: {
        labels: s.map((x) => x.mesRef.slice(2, 7).split('-').reverse().join('/')),
        datasets: [
          { label: this.uf(), data: s.map((x) => x.precoMedioUf), borderColor: '#ffb020', backgroundColor: 'rgba(255,176,32,.15)', fill: true, tension: 0.4, pointRadius: 0, borderWidth: 3 },
          { label: 'Brasil', data: s.map((x) => x.precoMedioNacional), borderColor: '#8b93ff', borderDash: [6, 6], tension: 0.4, pointRadius: 0, borderWidth: 2 },
        ],
      },
      options: {
        animation: { duration: 1600, easing: 'easeOutQuart' },
        interaction: { mode: 'index', intersect: false },
        plugins: { legend: { labels: { usePointStyle: true, boxWidth: 8 } } },
        scales: { x: { grid: { display: false }, ticks: { maxTicksLimit: 6 } }, y: { ticks: { callback: (v) => 'R$ ' + Number(v).toFixed(2) } } },
      },
    };
  });

  constructor() {
    this.api.produtos().subscribe((p) => {
      this.produtos.set(p);
      if (p.length && !p.includes(this.produto())) this.produto.set(p[0]);
      this.load();
    });
  }

  protected barW(r: Analise) { return Math.max(0.02, Math.abs(r.variacaoPct) / this.maxAbs()); }
  protected setProduto(p: string) { this.produto.set(p); this.load(); }
  protected setUf(u: string) { this.uf.set(u); this.loadSerie(); }

  private load() {
    this.loading.set(true);
    this.api.ranking(this.produto()).subscribe({
      next: (r) => {
        this.ranking.set(r);
        this.loading.set(false);
        if (r.length && !r.some((x) => x.uf === this.uf())) this.uf.set(r[0].uf);
        this.loadSerie();
      },
      error: () => this.loading.set(false),
    });
  }
  private loadSerie() { this.api.serie(this.produto(), this.uf()).subscribe((s) => this.serie.set(s)); }

  protected exportar() {
    downloadCsv(`ranking-${this.produto().toLowerCase().replace(/\s+/g, '-')}.csv`,
      this.ranking().map((r) => ({ mes: r.mesRef, uf: r.uf, produto: r.produto, preco_medio_uf: r.precoMedioUf, preco_medio_nacional: r.precoMedioNacional, variacao_pct: r.variacaoPct })));
  }
}
