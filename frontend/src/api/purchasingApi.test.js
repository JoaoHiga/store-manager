import { afterEach, describe, expect, it, vi } from 'vitest';
import { activateSupplier, createSupplier, deactivateSupplier, listSuppliers } from './purchasingApi';

function mockFetchOnce(status, body) {
  global.fetch = vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  });
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('purchasingApi', () => {
  it('listSuppliers requests every supplier when no filter is given', async () => {
    mockFetchOnce(200, [{ id: 1, name: 'Distribuidora Lima' }]);

    const result = await listSuppliers();

    expect(result).toEqual([{ id: 1, name: 'Distribuidora Lima' }]);
    expect(global.fetch.mock.calls[0][0]).toMatch(/\/api\/suppliers$/);
  });

  it('listSuppliers passes the active filter as a query parameter', async () => {
    mockFetchOnce(200, []);

    await listSuppliers({ active: false });

    expect(global.fetch.mock.calls[0][0]).toMatch(/\/api\/suppliers\?active=false$/);
  });

  it('createSupplier posts the supplier and throws the backend message on a duplicate RUC', async () => {
    mockFetchOnce(409, { message: 'A supplier with RUC 20131312955 already exists' });

    await expect(createSupplier({ name: 'Distribuidora Lima', ruc: '20131312955' })).rejects.toThrow(
      'A supplier with RUC 20131312955 already exists',
    );
    const [url, options] = global.fetch.mock.calls[0];
    expect(url).toMatch(/\/api\/suppliers$/);
    expect(options.method).toBe('POST');
    expect(JSON.parse(options.body)).toEqual({ name: 'Distribuidora Lima', ruc: '20131312955' });
  });

  it('deactivateSupplier and activateSupplier PATCH their endpoints and resolve to null on 204', async () => {
    mockFetchOnce(204, null);
    await expect(deactivateSupplier(3)).resolves.toBeNull();
    expect(global.fetch.mock.calls[0][0]).toMatch(/\/api\/suppliers\/3\/deactivate$/);
    expect(global.fetch.mock.calls[0][1].method).toBe('PATCH');

    mockFetchOnce(204, null);
    await expect(activateSupplier(3)).resolves.toBeNull();
    expect(global.fetch.mock.calls[0][0]).toMatch(/\/api\/suppliers\/3\/activate$/);
  });
});
