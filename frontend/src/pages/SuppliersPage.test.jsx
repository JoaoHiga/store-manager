import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import * as purchasingApi from '../api/purchasingApi';
import { SuppliersPage } from './SuppliersPage';

vi.mock('../api/purchasingApi');

const activeSupplier = { id: 1, name: 'Distribuidora Lima', ruc: '20131312955', phone: '987654321', active: true };
const inactiveSupplier = { id: 2, name: 'Alfa Accesorios', ruc: '20100000009', phone: null, active: false };

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <SuppliersPage />
    </QueryClientProvider>,
  );
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe('SuppliersPage', () => {
  it('lists every supplier with its active state', async () => {
    purchasingApi.listSuppliers.mockResolvedValue([inactiveSupplier, activeSupplier]);
    renderPage();

    const activeRow = (await screen.findByText('Distribuidora Lima')).closest('tr');
    const inactiveRow = screen.getByText('Alfa Accesorios').closest('tr');
    expect(purchasingApi.listSuppliers).toHaveBeenCalledWith();
    expect(within(activeRow).getByText('Activo')).toBeInTheDocument();
    expect(within(activeRow).getByText('20131312955')).toBeInTheDocument();
    expect(within(inactiveRow).getByText('Inactivo')).toBeInTheDocument();
  });

  it('creates a supplier and shows it in the list', async () => {
    purchasingApi.listSuppliers.mockResolvedValueOnce([]).mockResolvedValueOnce([activeSupplier]);
    purchasingApi.createSupplier.mockResolvedValue(activeSupplier);
    renderPage();

    await screen.findByText('No hay proveedores todavía.');
    await userEvent.type(screen.getByLabelText('Nombre'), 'Distribuidora Lima');
    await userEvent.type(screen.getByLabelText('RUC'), '20131312955');
    await userEvent.click(screen.getByRole('button', { name: 'Crear proveedor' }));

    await waitFor(() =>
      expect(purchasingApi.createSupplier).toHaveBeenCalledWith(
        expect.objectContaining({ name: 'Distribuidora Lima', ruc: '20131312955' }),
      ),
    );
    expect(await screen.findByText('Distribuidora Lima')).toBeInTheDocument();
    expect(screen.getByLabelText('Nombre')).toHaveValue('');
  });

  it('shows the backend error when the RUC is a duplicate', async () => {
    purchasingApi.listSuppliers.mockResolvedValue([activeSupplier]);
    purchasingApi.createSupplier.mockRejectedValue(new Error('A supplier with RUC 20131312955 already exists'));
    renderPage();

    await screen.findByText('Distribuidora Lima');
    await userEvent.type(screen.getByLabelText('Nombre'), 'Otro nombre');
    await userEvent.type(screen.getByLabelText('RUC'), '20131312955');
    await userEvent.click(screen.getByRole('button', { name: 'Crear proveedor' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('A supplier with RUC 20131312955 already exists');
  });

  it('deactivates an active supplier and reactivates an inactive one', async () => {
    purchasingApi.listSuppliers.mockResolvedValue([inactiveSupplier, activeSupplier]);
    purchasingApi.deactivateSupplier.mockResolvedValue(null);
    purchasingApi.activateSupplier.mockResolvedValue(null);
    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: 'Desactivar' }));
    await waitFor(() => expect(purchasingApi.deactivateSupplier).toHaveBeenCalledWith(1));

    await userEvent.click(screen.getByRole('button', { name: 'Reactivar' }));
    await waitFor(() => expect(purchasingApi.activateSupplier).toHaveBeenCalledWith(2));
  });

  it('shows an error when suppliers fail to load', async () => {
    purchasingApi.listSuppliers.mockRejectedValue(new Error('Network down'));
    renderPage();

    expect(await screen.findByRole('alert')).toHaveTextContent('Error al cargar proveedores: Network down');
  });
});
