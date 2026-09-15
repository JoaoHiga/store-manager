import { afterEach, describe, expect, it, vi } from 'vitest';
import { listSales, recordSale, retryVoucher, voidSale } from './salesApi';

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

describe('salesApi', () => {
  it('listSales returns the parsed JSON body on success', async () => {
    mockFetchOnce(200, [{ id: 1, status: 'COMPLETED' }]);

    const result = await listSales();

    expect(result).toEqual([{ id: 1, status: 'COMPLETED' }]);
  });

  it('recordSale posts the items, buyer reference, and voucher type and returns the created sale', async () => {
    // Regression: recordSale used to destructure only items/buyerDocumentType/buyerDocumentNumber,
    // silently dropping voucherType from the outgoing request body even though RecordSalePage
    // always passed it in — every submission failed the backend's @NotNull voucherType check.
    // RecordSalePage.test.jsx alone couldn't catch this: it mocks recordSale at the module
    // boundary and never inspects what this function actually sends over the wire.
    const sale = { id: 1, status: 'COMPLETED' };
    mockFetchOnce(201, sale);

    const result = await recordSale({
      items: [{ productId: 1, quantity: 2 }],
      buyerDocumentType: 'DNI',
      buyerDocumentNumber: '12345678',
      voucherType: 'BOLETA',
    });

    expect(global.fetch).toHaveBeenCalledWith(
      'http://localhost:8080/api/sales',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({
          items: [{ productId: 1, quantity: 2 }],
          buyerDocumentType: 'DNI',
          buyerDocumentNumber: '12345678',
          voucherType: 'BOLETA',
        }),
      }),
    );
    expect(result).toEqual(sale);
  });

  it('recordSale throws with the backend error message on failure', async () => {
    mockFetchOnce(400, { message: 'Sale must have at least one line item' });

    await expect(
      recordSale({ items: [], buyerDocumentType: 'DNI', buyerDocumentNumber: '12345678' }),
    ).rejects.toThrow('Sale must have at least one line item');
  });

  it('retryVoucher posts to the retry-voucher endpoint and returns the voucher', async () => {
    const voucher = { id: 1, saleId: 1, status: 'ACCEPTED' };
    mockFetchOnce(200, voucher);

    const result = await retryVoucher(1);

    expect(global.fetch).toHaveBeenCalledWith(
      'http://localhost:8080/api/sales/1/retry-voucher',
      expect.objectContaining({ method: 'POST' }),
    );
    expect(result).toEqual(voucher);
  });

  it('voidSale posts the reason to the void endpoint and returns the cancelled sale', async () => {
    const sale = { id: 1, status: 'CANCELLED' };
    mockFetchOnce(200, sale);

    const result = await voidSale(1, 'Cliente se arrepintió');

    expect(global.fetch).toHaveBeenCalledWith(
      'http://localhost:8080/api/sales/1/void',
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ reason: 'Cliente se arrepintió' }) }),
    );
    expect(result).toEqual(sale);
  });

  it('voidSale throws with the backend error message when the voucher is not voidable', async () => {
    mockFetchOnce(409, { message: 'Cannot void a voucher in status PENDING' });

    await expect(voidSale(1, 'Cliente se arrepintió')).rejects.toThrow(
      'Cannot void a voucher in status PENDING',
    );
  });
});
