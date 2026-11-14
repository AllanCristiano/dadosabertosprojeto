import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ApiService } from './api.service';

describe('ApiService', () => {
  let api: ApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('login faz POST com as credenciais', () => {
    api.login('demo', 'x').subscribe((r) => expect(r.accessToken).toBe('tok'));
    const req = http.expectOne('/api/v1/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ username: 'demo', password: 'x' });
    req.flush({ accessToken: 'tok', expiresInSeconds: 3600 });
  });

  it('produtos e ufs', () => {
    api.produtos().subscribe((p) => expect(p).toEqual(['GASOLINA']));
    api.ufs().subscribe((u) => expect(u).toEqual(['AC']));
    http.expectOne('/api/v1/analise/produtos').flush(['GASOLINA']);
    http.expectOne('/api/v1/analise/ufs').flush(['AC']);
  });

  it('ranking e série enviam produto/uf como query', () => {
    api.ranking('GASOLINA').subscribe();
    api.serie('GASOLINA', 'RR').subscribe();
    const r = http.expectOne((q) => q.url === '/api/v1/analise/ranking');
    expect(r.request.params.get('produto')).toBe('GASOLINA');
    r.flush([]);
    const s = http.expectOne((q) => q.url === '/api/v1/analise/serie');
    expect(s.request.params.get('uf')).toBe('RR');
    s.flush([]);
  });

  it('preços: sempre envia page/size e só os filtros preenchidos', () => {
    api.precos({ page: 2, size: 20, uf: 'SP', municipio: '', de: '2026-01-01' }).subscribe();
    const req = http.expectOne((q) => q.url === '/api/v1/precos');
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('20');
    expect(req.request.params.get('uf')).toBe('SP');
    expect(req.request.params.get('de')).toBe('2026-01-01');
    expect(req.request.params.has('municipio')).toBe(false);
    expect(req.request.params.has('produto')).toBe(false);
    req.flush({ content: [], page: 2, size: 20, totalElements: 0, totalPages: 0 });
  });
});
