import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Analise, Page, Preco, PrecoFiltro } from './models';

const BASE = '/api/v1';

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  login(username: string, password: string) {
    return this.http.post<{ accessToken: string; expiresInSeconds: number }>(`${BASE}/auth/login`, { username, password });
  }
  produtos() { return this.http.get<string[]>(`${BASE}/analise/produtos`); }
  ufs() { return this.http.get<string[]>(`${BASE}/analise/ufs`); }
  ranking(produto: string) {
    return this.http.get<Analise[]>(`${BASE}/analise/ranking`, { params: { produto } });
  }
  serie(produto: string, uf: string) {
    return this.http.get<Analise[]>(`${BASE}/analise/serie`, { params: { produto, uf } });
  }
  precos(f: PrecoFiltro) {
    let params = new HttpParams().set('page', f.page).set('size', f.size);
    for (const k of ['produto', 'uf', 'municipio', 'de', 'ate'] as const) {
      if (f[k]) params = params.set(k, f[k]!);
    }
    return this.http.get<Page<Preco>>(`${BASE}/precos`, { params });
  }
}
