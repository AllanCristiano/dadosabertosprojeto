import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { CountUpDirective, RevealDirective } from './motion';

@Component({
  imports: [RevealDirective, CountUpDirective],
  template: `<div id="r" appReveal [delay]="120"></div><span id="n" [appCountUp]="value()" [decimals]="1" prefix="+" suffix="%"></span>`,
})
class Host { value = signal(12.5); }

describe('RevealDirective', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('sem IntersectionObserver revela imediatamente', () => {
    vi.stubGlobal('IntersectionObserver', undefined);
    const f = TestBed.createComponent(Host);
    f.detectChanges();
    const el = f.nativeElement.querySelector('#r') as HTMLElement;
    expect(el.classList.contains('reveal')).toBe(true);
    expect(el.classList.contains('in')).toBe(true);
    expect(el.style.getPropertyValue('--d')).toBe('120ms');
  });

  it('revela só ao entrar no viewport e desconecta o observer', () => {
    let cb!: (e: { isIntersecting: boolean }[]) => void;
    const disconnect = vi.fn();
    vi.stubGlobal('IntersectionObserver', class { constructor(c: typeof cb) { cb = c; } observe() {} disconnect = disconnect; });
    const f = TestBed.createComponent(Host);
    f.detectChanges();
    const el = f.nativeElement.querySelector('#r') as HTMLElement;
    cb([{ isIntersecting: false }]);
    expect(el.classList.contains('in')).toBe(false);
    cb([{ isIntersecting: true }]);
    expect(el.classList.contains('in')).toBe(true);
    expect(disconnect).toHaveBeenCalled();
    f.destroy();
  });
});

describe('CountUpDirective', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('com prefers-reduced-motion mostra o valor final formatado em pt-BR', () => {
    vi.stubGlobal('matchMedia', () => ({ matches: true }));
    const f = TestBed.createComponent(Host);
    f.detectChanges();
    expect((f.nativeElement.querySelector('#n') as HTMLElement).textContent).toBe('+12,5%');
  });

  it('anima progressivamente até o valor alvo', () => {
    vi.stubGlobal('matchMedia', () => ({ matches: false }));
    let t = 0;
    const frames: FrameRequestCallback[] = [];
    vi.spyOn(performance, 'now').mockImplementation(() => t);
    vi.stubGlobal('requestAnimationFrame', (cb: FrameRequestCallback) => frames.push(cb));
    vi.stubGlobal('cancelAnimationFrame', vi.fn());
    const f = TestBed.createComponent(Host);
    f.detectChanges();
    const el = f.nativeElement.querySelector('#n') as HTMLElement;
    frames.shift()!(700);                       // meio da animação
    expect(el.textContent).not.toBe('+12,5%');
    frames.shift()!(5000);                      // fim (p = 1)
    expect(el.textContent).toBe('+12,5%');

    vi.restoreAllMocks();
  });

  it('atualiza o texto quando o valor muda', () => {
    vi.stubGlobal('matchMedia', () => ({ matches: true }));
    const f = TestBed.createComponent(Host);
    f.detectChanges();
    f.componentInstance.value.set(20);
    f.detectChanges();
    TestBed.tick();
    expect((f.nativeElement.querySelector('#n') as HTMLElement).textContent).toBe('+20,0%');
  });
});
