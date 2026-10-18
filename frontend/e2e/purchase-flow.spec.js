import { test, expect } from '@playwright/test';

const API_BASE_URL = 'http://localhost:8080';

const RUC_CHECK_DIGIT_WEIGHTS = [5, 4, 3, 2, 7, 6, 5, 4, 3, 2];

/**
 * A random RUC that passes the backend's `RucValidator` (prefix 20 + modulo-11 check digit), so
 * every run creates a supplier with a RUC no earlier run (on a reused container) has taken.
 */
function randomValidRuc() {
  const body = `20${String(Math.floor(Math.random() * 1e8)).padStart(8, '0')}`;
  const weightedSum = [...body].reduce((sum, digit, i) => sum + Number(digit) * RUC_CHECK_DIGIT_WEIGHTS[i], 0);
  const checkDigit = (11 - (weightedSum % 11)) % 10;
  return `${body}${checkDigit}`;
}

/**
 * Seeds one category + one product with known stock and cost directly against the real backend
 * API (the catalog UI isn't what this suite is testing).
 */
async function seedProduct(request, { stockQuantity, costPrice }) {
  const suffix = `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  const categoryResponse = await request.post(`${API_BASE_URL}/api/categories`, {
    data: { name: `Accesorios-${suffix}`, description: 'Accesorios' },
  });
  expect(categoryResponse.ok()).toBeTruthy();
  const category = await categoryResponse.json();

  const productResponse = await request.post(`${API_BASE_URL}/api/products`, {
    data: { name: `Funda-${suffix}`, categoryId: category.id, salePrice: 50.0, costPrice, stockQuantity },
  });
  expect(productResponse.ok()).toBeTruthy();
  return productResponse.json();
}

/** Reads one product back from the list endpoint (there is no `GET /api/products/{id}`). */
async function fetchProduct(request, categoryId, productId) {
  const response = await request.get(`${API_BASE_URL}/api/products?categoryId=${categoryId}`);
  expect(response.ok()).toBeTruthy();
  return (await response.json()).find((product) => product.id === productId);
}

/** Every page renders on one screen, so each page is scoped to the `<section>` owning its heading. */
function sectionTitled(page, title) {
  return page.locator('section').filter({ has: page.getByRole('heading', { name: title, exact: true }) });
}

async function createSupplierThroughTheUi(page, { name, ruc }) {
  const suppliers = sectionTitled(page, 'Proveedores');
  await suppliers.getByLabel('Nombre').fill(name);
  await suppliers.getByLabel('RUC').fill(ruc);
  await suppliers.getByRole('button', { name: 'Crear proveedor' }).click();
  await expect(suppliers.getByRole('row', { name: new RegExp(name) })).toContainText(ruc);
}

async function fillPurchaseForm(purchases, { supplierName, invoiceNumber, productName, quantity, unitCost }) {
  await purchases.getByLabel('Proveedor').selectOption({ label: supplierName });
  await purchases.getByLabel('Número de factura').fill(invoiceNumber);
  await purchases.getByLabel('Producto').selectOption({ label: productName });
  await purchases.getByLabel('Cantidad').fill(quantity);
  await purchases.getByLabel('Costo unitario').fill(unitCost);
}

test.describe('supplier -> purchase -> stock and cost (Phase B, full stack)', () => {
  test('recording a purchase lists it, raises stock, averages cost and rejects a duplicate invoice', async ({
    page,
    request,
  }) => {
    // Weighted average (feature decision): (10 * 100.00 + 5 * 130.00) / (10 + 5) = 110.00.
    const product = await seedProduct(request, { stockQuantity: 10, costPrice: 100.0 });
    const supplierName = `Distribuidora-${Date.now()}`;
    const invoiceNumber = `F001-${Date.now()}`;

    await page.goto('/');
    await createSupplierThroughTheUi(page, { name: supplierName, ruc: randomValidRuc() });

    const purchases = sectionTitled(page, 'Compras');
    await fillPurchaseForm(purchases, {
      supplierName,
      invoiceNumber,
      productName: product.name,
      quantity: '5',
      unitCost: '130',
    });
    await expect(purchases.getByText('Total: S/ 650.00')).toBeVisible();
    const purchaseDate = await purchases.getByLabel('Fecha').inputValue();

    const [recordResponse] = await Promise.all([
      page.waitForResponse(
        (res) => res.url() === `${API_BASE_URL}/api/purchases` && res.request().method() === 'POST',
      ),
      purchases.getByRole('button', { name: 'Registrar compra' }).click(),
    ]);
    expect(recordResponse.status()).toBe(201);
    const recordedPurchase = await recordResponse.json();

    await expect(purchases.getByRole('status')).toHaveText(`Compra #${recordedPurchase.id} registrada correctamente`);

    // The list reflects the real response: date, supplier, invoice, line count and total.
    const purchaseRow = purchases.getByRole('row', { name: new RegExp(invoiceNumber) });
    await expect(purchaseRow.getByRole('cell')).toHaveText([purchaseDate, supplierName, invoiceNumber, '1', 'S/ 650.00']);

    // The success handler invalidates products, so the products page shows the raised stock.
    // Asserts the Stock cell itself, located by its header, so a price or SKU containing "15" cannot match.
    const products = sectionTitled(page, 'Productos');
    await expect(products.getByRole('columnheader', { name: 'Stock', exact: true })).toBeVisible();
    const stockColumn = (await products.getByRole('columnheader').allTextContents()).indexOf('Stock');
    const productRow = products.getByRole('row', { name: new RegExp(product.name) });
    await expect(productRow.getByRole('cell').nth(stockColumn)).toHaveText('15');

    // Cost price is not shown in the UI; the backend is the source of truth for the average.
    const updatedProduct = await fetchProduct(request, product.categoryId, product.id);
    expect(updatedProduct.stockQuantity).toBe(15);
    expect(Number(updatedProduct.costPrice)).toBe(110);

    // Same supplier + invoice number again: the backend's 409 message is shown and the form keeps
    // what was typed so the user can correct it.
    await fillPurchaseForm(purchases, {
      supplierName,
      invoiceNumber,
      productName: product.name,
      quantity: '1',
      unitCost: '130',
    });
    const [duplicateResponse] = await Promise.all([
      page.waitForResponse(
        (res) => res.url() === `${API_BASE_URL}/api/purchases` && res.request().method() === 'POST',
      ),
      purchases.getByRole('button', { name: 'Registrar compra' }).click(),
    ]);
    expect(duplicateResponse.status()).toBe(409);
    await expect(purchases.getByRole('alert')).toContainText(`invoice number ${invoiceNumber} already exists`);
    await expect(purchases.getByRole('status')).toHaveCount(0);
    await expect(purchases.getByLabel('Número de factura')).toHaveValue(invoiceNumber);
    await expect(purchases.getByLabel('Cantidad')).toHaveValue('1');

    // The rejected purchase changed nothing.
    const afterDuplicate = await fetchProduct(request, product.categoryId, product.id);
    expect(afterDuplicate.stockQuantity).toBe(15);
    expect(Number(afterDuplicate.costPrice)).toBe(110);
  });
});
