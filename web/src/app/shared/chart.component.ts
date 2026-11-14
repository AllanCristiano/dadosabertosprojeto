import { ChangeDetectionStrategy, Component, ElementRef, NgZone, OnDestroy, effect, inject, input, viewChild } from '@angular/core';
import { Chart, ChartConfiguration } from 'chart.js/auto';

Chart.defaults.color = '#9aa1b8';
Chart.defaults.font.family = "'Sora', system-ui, sans-serif";
Chart.defaults.borderColor = 'rgba(255,255,255,0.07)';

/** Wrapper fino do Chart.js: recria o gráfico quando a config muda, animando a entrada. */
@Component({
  selector: 'app-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<canvas #c></canvas>`,
  styles: `:host { display: block; position: relative; height: 100%; } canvas { max-width: 100%; }`,
})
export class ChartComponent implements OnDestroy {
  readonly config = input.required<ChartConfiguration>();
  private readonly canvas = viewChild.required<ElementRef<HTMLCanvasElement>>('c');
  private readonly zone = inject(NgZone);
  private chart?: Chart;

  constructor() {
    effect(() => {
      const cfg = this.config();
      this.chart?.destroy();
      this.zone.runOutsideAngular(() => {
        this.chart = new Chart(this.canvas().nativeElement, {
          ...cfg,
          options: { responsive: true, maintainAspectRatio: false, ...cfg.options },
        });
      });
    });
  }
  ngOnDestroy() { this.chart?.destroy(); }
}
