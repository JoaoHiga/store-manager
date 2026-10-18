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
        voucherType: 'BOLETA',
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

    expect(screen.getByRole('alert')).toHaveTextContent('Seleccione un producto en cada línea');
    expect(salesApi.recordSale).not.toHaveBeenCalled();
  });

  it('offers DNI, RUC, CE and PASAPORTE as document type options', async () => {
    catalogApi.listProducts.mockResolvedValue([iphone]);
    renderPage();

    await screen.findByText('iPhone 12 (3 en stock)');
    const typeSelect = screen.getByLabelText('Tipo de documento');

    expect(within(typeSelect).getByText('DNI')).toBeInTheDocument();
    expect(within(typeSelect).getByText('RUC')).toBeInTheDocument();
    expect(within(typeSelect).getByText('CE')).toBeInTheDocument();
    expect(within(typeSelect).getByText('PASAPORTE')).toBeInTheDocument();
  });

  it('records a sale with no buyer document at all when "Sin documento" is chosen', async () => {
    catalogApi.listProducts.mockResolvedValue([iphone]);
    salesApi.recordSale.mockResolvedValue({ id: 9, status: 'COMPLETED' });
    renderPage();

    await screen.findByText('iPhone 12 (3 en stock)');
    await userEvent.selectOptions(screen.getByLabelText('Producto'), '1');
    await userEvent.type(screen.getByLabelText('Cantidad'), '1');
    await userEvent.click(screen.getByRole('checkbox', { name: /sin documento/i }));
    await userEvent.click(screen.getByRole('button', { name: 'Registrar venta' }));

    await waitFor(() => expect(salesApi.recordSale).toHaveBeenCalled());
    const payload = salesApi.recordSale.mock.calls[0][0];
    expect(payload.items).toEqual([{ productId: 1, quantity: 1 }]);
    expect(payload).not.toHaveProperty('buyerDocumentType');
    expect(payload).not.toHaveProperty('buyerDocumentNumber');
    expect(payload.voucherType).toBe('BOLETA');
  });

  it('hides the document type and number fields once "Sin documento" is checked', async () => {
    catalogApi.listProducts.mockResolvedValue([iphone]);
    renderPage();

    await screen.findByText('iPhone 12 (3 en stock)');
    await userEvent.click(screen.getByRole('checkbox', { name: /sin documento/i }));

    expect(screen.queryByLabelText('Tipo de documento')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Número de documento')).not.toBeInTheDocument();
  });

  it('does not require a document number when "Sin documento" is checked', async () => {
    catalogApi.listProducts.mockResolvedValue([iphone]);
    salesApi.recordSale.mockResolvedValue({ id: 11, status: 'COMPLETED' });
    renderPage();

    await screen.findByText('iPhone 12 (3 en stock)');
    await userEvent.selectOptions(screen.getByLabelText('Producto'), '1');
    await userEvent.type(screen.getByLabelText('Cantidad'), '1');
    await userEvent.click(screen.getByRole('checkbox', { name: /sin documento/i }));
    await userEvent.click(screen.getByRole('button', { name: 'Registrar venta' }));

    await waitFor(() => expect(salesApi.recordSale).toHaveBeenCalled());
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('offers Boleta and Factura as voucher type options, defaulting to Boleta', async () => {
    catalogApi.listProducts.mockResolvedValue([iphone]);
    renderPage();

    await screen.findByText('iPhone 12 (3 en stock)');
    const voucherTypeSelect = screen.getByLabelText('Tipo de comprobante');

    expect(within(voucherTypeSelect).getByText('Boleta')).toBeInTheDocument();
    expect(within(voucherTypeSelect).getByText('Factura')).toBeInTheDocument();
    expect(voucherTypeSelect).toHaveValue('BOLETA');
  });

  it('selecting Factura forces the document type to RUC and disables it and "Sin documento"', async () => {
    catalogApi.listProducts.mockResolvedValue([iphone]);
    renderPage();

    await screen.findByText('iPhone 12 (3 en stock)');
    await userEvent.selectOptions(screen.getByLabelText('Tipo de comprobante'), 'FACTURA');

    expect(screen.getByLabelText('Tipo de documento')).toHaveValue('RUC');
    expect(screen.getByLabelText('Tipo de documento')).toBeDisabled();
    expect(screen.getByRole('checkbox', { name: /sin documento/i })).toBeDisabled();
    expect(screen.getByRole('checkbox', { name: /sin documento/i })).not.toBeChecked();
  });

  it('selecting Boleta after Factura re-enables every document type option including RUC', async () => {
    catalogApi.listProducts.mockResolvedValue([iphone]);
    renderPage();

    await screen.findByText('iPhone 12 (3 en stock)');
    await userEvent.selectOptions(screen.getByLabelText('Tipo de comprobante'), 'FACTURA');
    await userEvent.selectOptions(screen.getByLabelText('Tipo de comprobante'), 'BOLETA');

    const typeSelect = screen.getByLabelText('Tipo de documento');
    expect(typeSelect).not.toBeDisabled();
    expect(within(typeSelect).getByText('DNI')).toBeInTheDocument();
    expect(within(typeSelect).getByText('RUC')).toBeInTheDocument();
    expect(within(typeSelect).getByText('CE')).toBeInTheDocument();
    expect(within(typeSelect).getByText('PASAPORTE')).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: /sin documento/i })).not.toBeDisabled();
  });

  it('submits a Factura for a RUC buyer including the voucherType in the payload', async () => {
    catalogApi.listProducts.mockResolvedValue([iphone]);
    salesApi.recordSale.mockResolvedValue({ id: 12, status: 'COMPLETED' });
    renderPage();

    await screen.findByText('iPhone 12 (3 en stock)');
    await userEvent.selectOptions(screen.getByLabelText('Producto'), '1');
    await userEvent.type(screen.getByLabelText('Cantidad'), '1');
    await userEvent.selectOptions(screen.getByLabelText('Tipo de comprobante'), 'FACTURA');
    await userEvent.type(screen.getByLabelText('Número de documento'), '20131312955');
    await userEvent.click(screen.getByRole('button', { name: 'Registrar venta' }));

    await waitFor(() =>
      expect(salesApi.recordSale).toHaveBeenCalledWith({
        items: [{ productId: 1, quantity: 1 }],
        buyerDocumentType: 'RUC',
        buyerDocumentNumber: '20131312955',
        voucherType: 'FACTURA',
      }),
    );
  });
});
