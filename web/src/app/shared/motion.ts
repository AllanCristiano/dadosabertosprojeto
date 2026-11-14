import { Directive, ElementRef, OnDestroy, OnInit, effect, inject, input } from '@angular/core';

/** Revela o elemento com fade/blur/slide quando entra no viewport. Uso: <div appReveal [delay]="120"> */
@Directive({ selector: '[appReveal]' })
export class RevealDirective implements OnInit, OnDestroy {
  readonly delay = input(0);
  private readonly el = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private io?: IntersectionObserver;

  ngOnInit() {
    this.el.classList.add('reveal');
    this.el.style.setProperty('--d', `${this.delay()}ms`);
    if (typeof IntersectionObserver === 'undefined') { this.el.classList.add('in'); return; }
    this.io = new IntersectionObserver(
      ([e]) => { if (e.isIntersecting) { this.el.classList.add('in'); this.io?.disconnect(); } },
      { threshold: 0.12 },
    );
    this.io.observe(this.el);
  }
  ngOnDestroy() { this.io?.disconnect(); }
}

/** Anima um número até o valor alvo (easeOutExpo). Uso: <span [appCountUp]="12.3" [decimals]="1" suffix="%"> */
@Directive({ selector: '[appCountUp]' })
export class CountUpDirective {
  readonly value = input.required<number>({ alias: 'appCountUp' });
  readonly decimals = input(0);
  readonly prefix = input('');
  readonly suffix = input('');
  readonly duration = input(1400);
  private readonly el = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private from = 0;
  private raf = 0;

  constructor() {
    effect((onCleanup) => {
      const to = this.value();
      const from = this.from;
      const dur = this.duration();
      const reduce = typeof matchMedia !== 'undefined' && matchMedia('(prefers-reduced-motion: reduce)').matches;
      const fmt = (n: number) =>
        this.prefix() + n.toLocaleString('pt-BR', { minimumFractionDigits: this.decimals(), maximumFractionDigits: this.decimals() }) + this.suffix();
      if (reduce) { this.el.textContent = fmt(to); this.from = to; return; }
      const t0 = performance.now();
      const tick = (t: number) => {
        const p = Math.min(1, (t - t0) / dur);
        const e = p === 1 ? 1 : 1 - Math.pow(2, -10 * p);
        this.from = from + (to - from) * e;
        this.el.textContent = fmt(this.from);
        if (p < 1) this.raf = requestAnimationFrame(tick);
      };
      this.raf = requestAnimationFrame(tick);
      onCleanup(() => cancelAnimationFrame(this.raf));
    });
  }
}
