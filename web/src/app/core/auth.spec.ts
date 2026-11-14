import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { AuthService, authGuard, authInterceptor } from './auth';

function setup() {
  TestBed.configureTestingModule({
    providers: [provideRouter([]), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
  });
  return { auth: TestBed.inject(AuthService), http: TestBed.inject(HttpClient), ctrl: TestBed.inject(HttpTestingController) };
}

describe('AuthService', () => {
  beforeEach(() => sessionStorage.clear());

  it('login guarda o token no signal e no sessionStorage', () => {
    const { auth, ctrl } = setup();
    auth.login('demo', 'demo').subscribe();
    ctrl.expectOne('/api/v1/auth/login').flush({ accessToken: 'abc', expiresInSeconds: 3600 });
    expect(auth.token()).toBe('abc');
    expect(sessionStorage.getItem('fuel_token')).toBe('abc');
  });

  it('logout limpa o token e volta ao login', () => {
    sessionStorage.setItem('fuel_token', 'abc');
    const { auth } = setup();
    const nav = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    expect(auth.token()).toBe('abc');
    auth.logout();
    expect(auth.token()).toBeNull();
    expect(sessionStorage.getItem('fuel_token')).toBeNull();
    expect(nav).toHaveBeenCalledWith('/login');
  });

  it('tolera sessionStorage indisponível', () => {
    const boom = () => { throw new Error('bloqueado'); };
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(boom);
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(boom);
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(boom);
    const { auth, ctrl } = setup();
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    expect(auth.token()).toBeNull();
    auth.login('a', 'b').subscribe();
    ctrl.expectOne('/api/v1/auth/login').flush({ accessToken: 't', expiresInSeconds: 1 });
    expect(auth.token()).toBe('t');
    expect(() => auth.logout()).not.toThrow();
    vi.restoreAllMocks();
  });
});

describe('authInterceptor', () => {
  beforeEach(() => sessionStorage.clear());

  it('anexa Bearer nas chamadas de dados, mas não no login', () => {
    sessionStorage.setItem('fuel_token', 'abc');
    const { http, ctrl } = setup();
    http.get('/api/v1/precos').subscribe();
    expect(ctrl.expectOne('/api/v1/precos').request.headers.get('Authorization')).toBe('Bearer abc');
    http.post('/api/v1/auth/login', {}).subscribe();
    expect(ctrl.expectOne('/api/v1/auth/login').request.headers.has('Authorization')).toBe(false);
  });

  it('sem token não envia Authorization', () => {
    const { http, ctrl } = setup();
    http.get('/api/v1/precos').subscribe();
    expect(ctrl.expectOne('/api/v1/precos').request.headers.has('Authorization')).toBe(false);
  });

  it('401 em endpoint de dados faz logout; 401 no login não', () => {
    sessionStorage.setItem('fuel_token', 'abc');
    const { auth, http, ctrl } = setup();
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    http.post('/api/v1/auth/login', {}).subscribe({ error: () => {} });
    ctrl.expectOne('/api/v1/auth/login').flush('x', { status: 401, statusText: 'Unauthorized' });
    expect(auth.token()).toBe('abc');
    http.get('/api/v1/precos').subscribe({ error: () => {} });
    ctrl.expectOne('/api/v1/precos').flush('x', { status: 401, statusText: 'Unauthorized' });
    expect(auth.token()).toBeNull();
  });

  it('outros erros (500) propagam sem logout', () => {
    sessionStorage.setItem('fuel_token', 'abc');
    const { auth, http, ctrl } = setup();
    let status = 0;
    http.get('/api/v1/precos').subscribe({ error: (e) => (status = e.status) });
    ctrl.expectOne('/api/v1/precos').flush('x', { status: 500, statusText: 'Server Error' });
    expect(status).toBe(500);
    expect(auth.token()).toBe('abc');
  });
});

describe('authGuard', () => {
  beforeEach(() => sessionStorage.clear());

  it('libera com token e redireciona ao login sem token', () => {
    const { auth } = setup();
    auth.token.set('abc');
    expect(TestBed.runInInjectionContext(() => authGuard({} as never, {} as never))).toBe(true);
    auth.token.set(null);
    const r = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));
    expect(r instanceof UrlTree && r.toString()).toBe('/login');
  });
});
