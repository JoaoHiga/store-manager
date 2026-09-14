import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import * as catalogApi from '../api/catalogApi';
import * as salesApi from '../api/salesApi';
import { RecordSalePage } from './RecordSalePage';

vi.mock('../api/catalogApi');
vi.mock('../api/salesApi');

const iphone = { id: 1, sku: 'PRD-000001', name: 'iPhone 12', active: true, stockQuantity: 3, salePrice: 350 };
const outOfStock = { id: 2, sku: 'PRD-000002', name: 'Sin stock', active: true, stockQuantity: 0, salePrice: 100 };

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <RecordSalePage />
    </QueryClientProvider>,
  );
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('RecordSalePage', () => {
  it('only lists active products with stock available in the product picker', async () => {
    catalogApi.listProducts.mockResolvedValue([iphone, outOfStock]);
    renderPage();

    await screen.findByText('iPhone 12 (3 en stock)');
    const picker = screen.getByLabelText('Producto');
    expect(within(picker).getByText('iPhone 12 (3 en stock)')).toBeInTheDocument();
    expect(within(picker).queryByText(/Sin stock/)).not.toBeInTheDocument();
  });

  it('records a sale with the entered line item and buyer reference', async () => {
    catalogApi.listProducts.mockResolvedValue([iphone]);
    salesApi.recordSale.mockResolvedValue({ id: 7, status: 'COMPLETED' });
    renderPage();

    await screen.findByText('iPhone 12 (3 en stock)');

    await userEvent.selectOptions(screen.getByLabelText('Producto'), '1');
    await userEvent.type(screen.getByLabelText('Cantidad'), '2');
    await userEvent.selectOptions(screen.getByLabelText('Tipo de documento'), 'DNI');
    await userEvent.type(screen.getByLabelText('Número de documento'), '12345678');
    await userEvent.click(screen.getByRole('button', { name: 'Registrar venta' }));

    await waitFor(() =>
      expect(salesApi.recordSale).toHaveBeenCalledWith({
        items: [{ productId: 1, quantity: 2 }],
        buyerDocumentType: 'DNI',
        buyerDocumentNumber: '12345678',
      }),
    );
    expect(await screen.findByText('Venta #7 registrada correctamente')).toBeInTheDocument();
  });

  it('supports adding a second line item and submits both', async () => {
    const secondProduct = { id: 3, sku: 'PRD-000003', name: 'Cargador', active: true, stockQuantity: 5, salePrice: 20 };
    catalogApi.listProducts.mockResolvedValue([iphone, secondProduct]);
    salesApi.recordSale.mockResolvedValue({ id: 8, status: 'COMPLETED' });
    renderPage();

    await screen.findByText('iPhone 12 (3 en stock)');
    await userEvent.click(screen.getByRole('button', { name: 'Agregar línea' }));

    const pickers = screen.getAllByLabelText('Producto');
    const quantities = screen.getAllByLabelText('Cantidad');
    await userEvent.selectOptions(pickers[0], '1');
    await userEvent.type(quantities[0], '1');
    await userEvent.selectOptions(pickers[1], '3');
    await userEvent.type(quantities[1], '2');
    await userEvent.type(screen.getByLabelText('Número de documento'), '12345678');
    await userEvent.click(screen.getByRole('button', { name: 'Registrar venta' }));

    await waitFor(() =>
      expect(salesApi.recordSale).toHaveBeenCalledWith(
        expect.objectContaining({
          items: [
            { productId: 1, quantity: 1 },
            { productId: 3, quantity: 2 },
          ],
        }),
      ),
    );
  });

  it('shows an error message when recording a sale fails', async () => {
    catalogApi.listProducts.mockResolvedValue([iphone]);
    salesApi.recordSale.mockRejectedValue(new Error('El producto no tiene stock suficiente'));
    renderPage();

    await screen.findByText('iPhone 12 (3 en stock)');

    await userEvent.selectOptions(screen.getByLabelText('Producto'), '1');
    await userEvent.type(screen.getByLabelText('Cantidad'), '2');
    await userEvent.type(screen.getByLabelText('Número de documento'), '12345678');
    await userEvent.click(screen.getByRole('button', { name: 'Registrar venta' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('El producto no tiene stock suficiente');
  });

  it('rejects submitting without choosing a product', async () => {
    catalogApi.listProducts.mockResolvedValue([iphone]);
    renderPage();

    await screen.findByText('iPhone 12 (3 en stock)');
    await userEvent.type(screen.getByLabelText('Número de documento'), '12345678');
    await userEvent.click(screen.getByRole('button', { name: 'Registrar venta' }));

    expect(screen.getByRole('alert')).toHaveTextContent('Elegí un producto en cada línea');
    expect(salesApi.recordSale).not.toHaveBeenCalled();
  });
});
