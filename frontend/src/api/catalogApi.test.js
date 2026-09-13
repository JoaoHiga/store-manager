import { afterEach, describe, expect, it, vi } from 'vitest';
import { createCategory, deactivateProduct, listCategories } from './catalogApi';

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

describe('catalogApi', () => {
  it('listCategories returns the parsed JSON body on success', async () => {
    mockFetchOnce(200, [{ id: 1, name: 'Celulares' }]);

    const result = await listCategories();

    expect(result).toEqual([{ id: 1, name: 'Celulares' }]);
  });

  it('createCategory throws with the backend error message on failure', async () => {
    mockFetchOnce(409, { message: "A category named 'Celulares' already exists" });

    await expect(createCategory({ name: 'Celulares' })).rejects.toThrow(
      "A category named 'Celulares' already exists",
    );
  });

  it('deactivateProduct resolves to null on a 204 response', async () => {
    mockFetchOnce(204, null);

    await expect(deactivateProduct(1)).resolves.toBeNull();
  });
});
