import { test, expect } from '@playwright/test';

const API_BASE_URL = 'http://localhost:8080';

/**
 * Seeds one category + one product directly against the real backend API
 * (not through the UI — the catalog UI isn't what this suite is testing).
 * Returns the product's name so the test can pick it in RecordSalePage's
 * `<select>`.
 */
async function seedProduct(request) {
  const categoryName = `Celulares-${Date.now()}-${Math.random().toString(36).slice(2)}`;
  const categoryResponse = await request.post(`${API_BASE_URL}/api/categories`, {
    data: { name: categoryName, description: 'Smartphones' },
  });
  expect(categoryResponse.ok()).toBeTruthy();
  const category = await categoryResponse.json();

  const productName = `iPhone-${Date.now()}`;
  const productResponse = await request.post(`${API_BASE_URL}/api/products`, {
    data: {
      name: productName,
      categoryId: category.id,
      salePrice: 900.0,
      stockQuantity: 10,
    },
  });
  expect(productResponse.ok()).toBeTruthy();
  return productResponse.json();
}

/**
 * Fills and submits RecordSalePage for one product, capturing the real
 * outgoing POST body, the real POST response, and the real GET /api/sales
 * response the success handler triggers. Shared by both voucher-type
 * scenarios (triangulation) so each test only states what differs.
 */
async function recordSaleThroughTheRealUi(page, productId, { voucherType, buyerDocumentType, buyerDocumentNumber }) {
  await page.goto('/');
  await page.selectOption('#product-0', String(productId));
  await page.fill('#quantity-0', '1');
  await page.selectOption('#voucherType', voucherType);
  if (buyerDocumentType !== 'RUC') {
    await page.selectOption('#buyerDocumentType', buyerDocumentType);
  }
  await page.fill('#buyerDocumentNumber', buyerDocumentNumber);

  const [recordRequest, recordResponse] = await Promise.all([
    page.waitForRequest((req) => req.url() === `${API_BASE_URL}/api/sales` && req.method() === 'POST'),
    page.waitForResponse((res) => res.url() === `${API_BASE_URL}/api/sales` && res.request().method() === 'POST'),
    page.getByRole('button', { name: 'Registrar venta' }).click(),
  ]);

  await expect(page.getByRole('status')).toContainText('registrada correctamente');

  // Success triggers a `GET /api/sales` refetch (RecordSalePage's onSuccess invalidates the
  // 'sales' query) — the exact bug class from `explicit-voucher-type-selection` itself: the
  // list must reflect the REAL voucher type, not a derived/shared/defaulted one.
  const listResponse = await page.waitForResponse(
    (res) => res.url() === `${API_BASE_URL}/api/sales` && res.request().method() === 'GET',
  );

  return {
    sentBody: recordRequest.postDataJSON(),
    recordResponse,
    recordedSale: await recordResponse.json(),
    sales: await listResponse.json(),
  };
}

test.describe('sale record -> voucher type -> list (Phase B, full stack)', () => {
  test('recording a FACTURA sale sends the real request and the list reflects the real response', async ({
    page,
    request,
  }) => {
    const product = await seedProduct(request);

    const { sentBody, recordResponse, recordedSale, sales } = await recordSaleThroughTheRealUi(page, product.id, {
      voucherType: 'FACTURA',
      buyerDocumentType: 'RUC',
      buyerDocumentNumber: '20131312955',
    });

    // The exact bug class from `explicit-voucher-type-selection`'s follow-up fix (PR #22): the
    // frontend must actually SEND voucherType in the real outgoing request body.
    expect(sentBody.voucherType).toBe('FACTURA');
    expect(recordResponse.status()).toBe(201);
    expect(recordedSale.voucherType).toBe('FACTURA');

    const listedSale = sales.find((sale) => sale.id === recordedSale.id);
    expect(listedSale.voucherType).toBe('FACTURA');
  });

  test('recording a BOLETA sale sends the real request and the list reflects the real response', async ({
    page,
    request,
  }) => {
    const product = await seedProduct(request);

    const { sentBody, recordResponse, recordedSale, sales } = await recordSaleThroughTheRealUi(page, product.id, {
      voucherType: 'BOLETA',
      buyerDocumentType: 'DNI',
      buyerDocumentNumber: '12345678',
    });

    expect(sentBody.voucherType).toBe('BOLETA');
    expect(recordResponse.status()).toBe(201);
    expect(recordedSale.voucherType).toBe('BOLETA');

    const listedSale = sales.find((sale) => sale.id === recordedSale.id);
    expect(listedSale.voucherType).toBe('BOLETA');
  });
});
