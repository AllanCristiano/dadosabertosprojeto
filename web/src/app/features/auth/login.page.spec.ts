import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { LoginPage } from './login.page';

describe('LoginPage', () => {
  function criar() {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    const fixture = TestBed.createComponent(LoginPage);
    fixture.detectChanges();
    const nav = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    return { fixture, nav, ctrl: TestBed.inject(HttpTestingController), el: fixture.nativeElement as HTMLElement };
  }

  it('renderiza o título animado e o formulário', () => {
    const { el } = criar();
    expect(el.querySelectorAll('h1 .w').length).toBe(3);
    expect(el.querySelector('button')?.textContent).toContain('Entrar');
  });

  it('login válido navega para o painel', () => {
    const { fixture, nav, ctrl } = criar();
    fixture.componentInstance.submit();
    ctrl.expectOne('/api/v1/auth/login').flush({ accessToken: 'tok', expiresInSeconds: 3600 });
    expect(nav).toHaveBeenCalledWith('/painel');
  });

  it('401 mostra credenciais inválidas e reabilita o botão', () => {
    const { fixture, nav, ctrl, el } = criar();
    fixture.componentInstance.submit();
    ctrl.expectOne('/api/v1/auth/login').flush('x', { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();
    expect(el.querySelector('[role=alert]')?.textContent).toContain('inválidos');
    expect(el.querySelector('button')?.disabled).toBe(false);
    expect(nav).not.toHaveBeenCalled();
  });

  it('erro de rede mostra API indisponível', () => {
    const { fixture, ctrl, el } = criar();
    fixture.componentInstance.submit();
    ctrl.expectOne('/api/v1/auth/login').flush('x', { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();
    expect(el.querySelector('[role=alert]')?.textContent).toContain('indisponível');
  });
});
