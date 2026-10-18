import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import * as catalogApi from '../api/catalogApi';
import * as purchasingApi from '../api/purchasingApi';
import { PurchasesPage } from './PurchasesPage';

vi.mock('../api/catalogApi');
vi.mock('../api/purchasingApi');

const supplier = { id: 1, name: 'Distribuidora Lima', ruc: '20131312955', active: true };
const iphone = { id: 5, name: 'iPhone 12', active: true, stockQuantity: 3 };
const purchase = {
  id: 4,
  supplierId: 1,
  supplierName: 'Distribuidora Lima',
  invoiceNumber: 'F001-123',
  purchaseDate: '2026-10-16',
  total: 660,
  lineCount: 1,
  createdAt: '2026-10-16T15:00:00Z',
};

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <PurchasesPage />
    </QueryClientProvider>,
  );
}

async function fillAndSubmit() {
  await userEvent.selectOptions(await screen.findByLabelText('Proveedor'), '1');
  await userEvent.type(screen.getByLabelText('Número de factura'), 'F001-123');
  await userEvent.selectOptions(screen.getByLabelText('Producto'), '5');
  await userEvent.type(screen.getByLabelText('Cantidad'), '2');
  await userEvent.type(screen.getByLabelText('Costo unitario'), '330');
  await userEvent.click(screen.getByRole('button', { name: 'Registrar compra' }));
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('PurchasesPage', () => {
  it('offers only active suppliers and products, and lists purchases', async () => {
    purchasingApi.listSuppliers.mockResolvedValue([supplier]);
    catalogApi.listProducts.mockResolvedValue([iphone]);
    purchasingApi.listPurchases.mockResolvedValue([purchase]);
    renderPage();

    const row = (await screen.findByText('F001-123')).closest('tr');
    expect(within(row).getByText('Distribuidora Lima')).toBeInTheDocument();
    expect(within(row).getByText('2026-10-16')).toBeInTheDocument();
    expect(within(row).getByText('S/ 660.00')).toBeInTheDocument();
    expect(purchasingApi.listSuppliers).toHaveBeenCalledWith({ active: true });
    expect(catalogApi.listProducts).toHaveBeenCalledWith({ active: true });
    expect(within(screen.getByLabelText('Proveedor')).getByText('Distribuidora Lima')).toBeInTheDocument();
    expect(within(screen.getByLabelText('Producto')).getByText('iPhone 12')).toBeInTheDocument();
  });

  it('records a purchase, refreshes the list and clears the form', async () => {
    purchasingApi.listSuppliers.mockResolvedValue([supplier]);
    catalogApi.listProducts.mockResolvedValue([iphone]);
    purchasingApi.listPurchases.mockResolvedValueOnce([]).mockResolvedValueOnce([purchase]);
    purchasingApi.recordPurchase.mockResolvedValue({ ...purchase, items: [] });
    renderPage();

    await screen.findByText('No hay compras todavía.');
    await fillAndSubmit();

    await waitFor(() =>
      expect(purchasingApi.recordPurchase).toHaveBeenCalledWith(
        expect.objectContaining({
          supplierId: 1,
          invoiceNumber: 'F001-123',
          items: [{ productId: 5, quantity: 2, unitCost: 330 }],
        }),
      ),
    );
    expect(await screen.findByRole('status')).toHaveTextContent('Compra #4 registrada correctamente');
    expect(await screen.findByText('F001-123')).toBeInTheDocument();
    expect(screen.getByLabelText('Número de factura')).toHaveValue('');
  });

  it('shows the backend error when the invoice is a duplicate', async () => {
    purchasingApi.listSuppliers.mockResolvedValue([supplier]);
    catalogApi.listProducts.mockResolvedValue([iphone]);
    purchasingApi.listPurchases.mockResolvedValue([]);
    purchasingApi.recordPurchase.mockRejectedValue(new Error('Invoice F001-123 is already recorded for this supplier'));
    renderPage();

    await screen.findByText('No hay compras todavía.');
    await fillAndSubmit();

    expect(await screen.findByRole('alert')).toHaveTextContent('Invoice F001-123 is already recorded for this supplier');
    expect(screen.getByLabelText('Número de factura')).toHaveValue('F001-123');
  });

  it('shows an error when purchases fail to load', async () => {
    purchasingApi.listSuppliers.mockResolvedValue([]);
    catalogApi.listProducts.mockResolvedValue([]);
    purchasingApi.listPurchases.mockRejectedValue(new Error('Network down'));
    renderPage();

    expect(await screen.findByRole('alert')).toHaveTextContent('Error al cargar compras: Network down');
  });

  it.each([
    ['suppliers', () => purchasingApi.listSuppliers, 'Error al cargar proveedores: Network down'],
    ['products', () => catalogApi.listProducts, 'Error al cargar productos: Network down'],
  ])('shows an error instead of the form when %s fail to load', async (_, failingApi, message) => {
    purchasingApi.listSuppliers.mockResolvedValue([supplier]);
    catalogApi.listProducts.mockResolvedValue([iphone]);
    purchasingApi.listPurchases.mockResolvedValue([purchase]);
    failingApi().mockRejectedValue(new Error('Network down'));
    renderPage();

    expect(await screen.findByRole('alert')).toHaveTextContent(message);
    expect(screen.queryByRole('button', { name: 'Registrar compra' })).not.toBeInTheDocument();
    // The purchase list does not depend on those queries and still renders.
    expect(await screen.findByText('F001-123')).toBeInTheDocument();
  });
});
