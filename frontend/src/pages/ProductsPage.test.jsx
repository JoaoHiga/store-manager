import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import * as catalogApi from '../api/catalogApi';
import { ProductsPage } from './ProductsPage';

vi.mock('../api/catalogApi');

const category = { id: 1, name: 'Celulares' };
const product = {
  id: 1,
  sku: 'PRD-000001',
  name: 'iPhone 12',
  categoryId: 1,
  salePrice: 350,
  stockQuantity: 3,
  active: true,
};

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <ProductsPage />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  catalogApi.listCategories.mockResolvedValue([category]);
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('ProductsPage', () => {
  it('creates a product and shows it in the list', async () => {
    catalogApi.listProducts.mockResolvedValueOnce([]).mockResolvedValueOnce([product]);
    catalogApi.createProduct.mockResolvedValue(product);
    renderPage();

    await screen.findByText('No hay productos todavía.');

    await userEvent.type(screen.getByLabelText('Nombre'), 'iPhone 12');
    await userEvent.selectOptions(screen.getByLabelText('Categoría'), '1');
    await userEvent.type(screen.getByLabelText('Precio de venta'), '350');
    await userEvent.type(screen.getByLabelText('Stock'), '3');
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }));

    await waitFor(() => expect(catalogApi.createProduct).toHaveBeenCalled());
    expect(await screen.findByText('PRD-000001')).toBeInTheDocument();
  });

  it('edits a product by loading it into the form and calling updateProduct', async () => {
    catalogApi.listProducts.mockResolvedValue([product]);
    catalogApi.updateProduct.mockResolvedValue({ ...product, salePrice: 320 });
    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: 'Editar' }));
    expect(screen.getByLabelText('Nombre')).toHaveValue('iPhone 12');

    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }));

    await waitFor(() =>
      expect(catalogApi.updateProduct).toHaveBeenCalledWith(
        1,
        expect.objectContaining({ name: 'iPhone 12', salePrice: 350 }),
      ),
    );
  });

  it('deactivates a product', async () => {
    catalogApi.listProducts.mockResolvedValue([product]);
    catalogApi.deactivateProduct.mockResolvedValue(null);
    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: 'Desactivar' }));

    await waitFor(() => expect(catalogApi.deactivateProduct).toHaveBeenCalledWith(1));
  });

  it('shows an error message when creating a product fails', async () => {
    catalogApi.listProducts.mockResolvedValue([]);
    catalogApi.createProduct.mockRejectedValue(new Error('El servidor rechazó la solicitud'));
    renderPage();

    await screen.findByText('No hay productos todavía.');

    await userEvent.type(screen.getByLabelText('Nombre'), 'iPhone 12');
    await userEvent.selectOptions(screen.getByLabelText('Categoría'), '1');
    await userEvent.type(screen.getByLabelText('Precio de venta'), '350');
    await userEvent.type(screen.getByLabelText('Stock'), '3');
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('El servidor rechazó la solicitud');
  });

  it('shows an error message when deactivating a product fails', async () => {
    catalogApi.listProducts.mockResolvedValue([product]);
    catalogApi.deactivateProduct.mockRejectedValue(new Error('El servidor rechazó la solicitud'));
    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: 'Desactivar' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('El servidor rechazó la solicitud');
  });

  it('toggling "Ver productos inactivos" lists inactive products and lets you reactivate one', async () => {
    const inactiveProduct = { ...product, active: false };
    catalogApi.listProducts.mockResolvedValueOnce([]).mockResolvedValueOnce([inactiveProduct]);
    catalogApi.activateProduct.mockResolvedValue(null);
    renderPage();

    await screen.findByText('No hay productos todavía.');

    await userEvent.click(screen.getByLabelText('Ver productos inactivos'));

    await waitFor(() => expect(catalogApi.listProducts).toHaveBeenCalledWith({ active: false }));
    await userEvent.click(await screen.findByRole('button', { name: 'Reactivar' }));

    await waitFor(() => expect(catalogApi.activateProduct).toHaveBeenCalledWith(1));
  });
});
