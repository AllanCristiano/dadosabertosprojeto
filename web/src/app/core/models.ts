export interface Analise {
  mesRef: string;
  uf: string;
  produto: string;
  precoMedioUf: number;
  precoMedioNacional: number;
  variacaoPct: number;
  acimaDaMedia: boolean;
}

export interface Preco {
  data: string;
  produto: string;
  uf: string;
  municipio: string;
  revenda: string;
  bandeira?: string;
  precoVenda: number;
  precoCompra?: number;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface PrecoFiltro {
  produto?: string;
  uf?: string;
  municipio?: string;
  de?: string;
  ate?: string;
  page: number;
  size: number;
}
