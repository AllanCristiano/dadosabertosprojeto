import { TestBed } from '@angular/core/testing';
import { downloadCsv } from './shared/csv';

describe('downloadCsv', () => {
  it('não faz nada com lista vazia', () => {
    const spy = vi.spyOn(URL, 'createObjectURL');
    downloadCsv('x.csv', []);
    expect(spy).not.toHaveBeenCalled();
  });

  it('gera CSV com separador ; e escapa aspas', async () => {
    let blob!: Blob;
    URL.createObjectURL = vi.fn((b: Blob) => { blob = b; return 'blob:x'; });
    URL.revokeObjectURL = vi.fn();
    downloadCsv('x.csv', [{ a: 'x;y', b: 'say "hi"', c: 1 }]);
    const text = await blob.text();
    expect(text).toContain('a;b;c');
    expect(text).toContain('"x;y";"say ""hi""";1');
  });
});
