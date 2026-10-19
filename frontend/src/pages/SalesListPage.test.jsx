import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import * as salesApi from '../api/salesApi';
import { SalesListPage } from './SalesListPage';

vi.mock('../api/salesApi');

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return {
    queryClient,
    ...render(
      <QueryClientProvider client={queryClient}>
        <SalesListPage />
      </QueryClientProvider>,
    ),
  };
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('SalesListPage', () => {
  it('shows an unknown-voucher badge when voucherStatus is null', async () => {
    salesApi.listSales.mockResolvedValue([
      { id: 1, buyerDocumentType: 'DNI', buyerDocumentNumber: '12345678', total: 350, status: 'COMPLETED', voucherStatus: null },
    ]);
    renderPage();

    expect(await screen.findByText('Desconocido')).toBeInTheDocument();
  });

  it('shows a translated badge for a known voucher status', async () => {
    salesApi.listSales.mockResolvedValue([
      { id: 1, buyerDocumentType: 'DNI', buyerDocumentNumber: '12345678', total: 350, status: 'COMPLETED', voucherStatus: 'PENDING' },
    ]);
    renderPage();

    expect(await screen.findByText('Pendiente')).toBeInTheDocument();
  });

  it('retries a pending voucher and reflects the accepted status afterwards', async () => {
    salesApi.listSales.mockResolvedValue([
      { id: 1, buyerDocumentType: 'DNI', buyerDocumentNumber: '12345678', total: 350, status: 'COMPLETED', voucherStatus: 'PENDING' },
    ]);
    salesApi.retryVoucher.mockResolvedValue({ id: 1, saleId: 1, status: 'ACCEPTED' });
    renderPage();

    await screen.findByText('Pendiente');
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }));

    await waitFor(() => expect(salesApi.retryVoucher).toHaveBeenCalledWith(1));
    expect(await screen.findByText('Aceptado')).toBeInTheDocument();
    // Once accepted, retrying no longer makes sense — only Anular should remain.
    expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Anular' })).toBeInTheDocument();
  });

  it('shows an error message when retrying a voucher fails', async () => {
    salesApi.listSales.mockResolvedValue([
      { id: 1, buyerDocumentType: 'DNI', buyerDocumentNumber: '12345678', total: 350, status: 'COMPLETED', voucherStatus: 'NEEDS_ATTENTION' },
    ]);
    salesApi.retryVoucher.mockRejectedValue(new Error('NubeFacT rechazó el comprobante'));
    renderPage();

    await screen.findByText('Requiere atención');
    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('NubeFacT rechazó el comprobante');
  });

  it('voids an accepted sale after confirming a reason, updates the row, and invalidates products', async () => {
    salesApi.listSales.mockResolvedValue([
      { id: 1, buyerDocumentType: 'RUC', buyerDocumentNumber: '20123456789', total: 500, status: 'COMPLETED', voucherStatus: 'ACCEPTED' },
    ]);
    salesApi.voidSale.mockResolvedValue({ id: 1, status: 'CANCELLED' });
    vi.spyOn(window, 'prompt').mockReturnValue('Cliente se arrepintió');
    const { queryClient } = renderPage();
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    await screen.findByText('Aceptado');
    // Only Anular applies to an ACCEPTED voucher.
    expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Anular' }));

    await waitFor(() => expect(salesApi.voidSale).toHaveBeenCalledWith(1, 'Cliente se arrepintió'));
    expect(await screen.findByText('Anulado')).toBeInTheDocument();
    expect(screen.getByText('CANCELLED')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Anular' })).not.toBeInTheDocument();
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['products'] });
  });

  it('does not void when the reason prompt is cancelled', async () => {
    salesApi.listSales.mockResolvedValue([
      { id: 1, buyerDocumentType: 'RUC', buyerDocumentNumber: '20123456789', total: 500, status: 'COMPLETED', voucherStatus: 'ACCEPTED' },
    ]);
    vi.spyOn(window, 'prompt').mockReturnValue(null);
    renderPage();

    await screen.findByText('Aceptado');
    await userEvent.click(screen.getByRole('button', { name: 'Anular' }));

    expect(salesApi.voidSale).not.toHaveBeenCalled();
  });

  it('hides both actions for an already-voided voucher', async () => {
    salesApi.listSales.mockResolvedValue([
      { id: 1, buyerDocumentType: 'RUC', buyerDocumentNumber: '20123456789', total: 500, status: 'CANCELLED', voucherStatus: 'VOIDED' },
    ]);
    renderPage();

    await screen.findByText('Anulado');
    expect(screen.queryByRole('button', { name: 'Reintentar' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Anular' })).not.toBeInTheDocument();
  });

  it('shows a message when there are no sales', async () => {
    salesApi.listSales.mockResolvedValue([]);
    renderPage();

    expect(await screen.findByText('No hay ventas todavía.')).toBeInTheDocument();
  });
});
