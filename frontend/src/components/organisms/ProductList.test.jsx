import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ProductList } from './ProductList';

const categories = [{ id: 1, name: 'Celulares' }];
const products = [
  {
    id: 1,
    sku: 'PRD-000001',
    name: 'iPhone 12',
    categoryId: 1,
    costPrice: 300,
    salePrice: 350,
    stockQuantity: 3,
    active: true,
  },
];

function renderList(overrides = {}) {
  return render(
    <ProductList
      products={products}
      categories={categories}
      onEdit={vi.fn()}
      onDeactivate={vi.fn()}
      onActivate={vi.fn()}
      {...overrides}
    />,
  );
}

describe('ProductList', () => {
  it('shows a message when there are no products', () => {
    renderList({ products: [] });

    expect(screen.getByText('No hay productos todavía.')).toBeInTheDocument();
  });

  it('renders each product with its resolved category name, cost price and sale price', () => {
    renderList();

    expect(screen.getByRole('columnheader', { name: 'Costo' })).toBeInTheDocument();
    expect(screen.getByText('PRD-000001')).toBeInTheDocument();
    expect(screen.getByText('iPhone 12')).toBeInTheDocument();
    expect(screen.getByText('Celulares')).toBeInTheDocument();
    expect(screen.getByText(/300,00/)).toBeInTheDocument();
    expect(screen.getByText(/350,00/)).toBeInTheDocument();
  });

  it('renders a dash when a product has no cost price', () => {
    renderList({ products: [{ ...products[0], costPrice: null }] });

    const headers = screen.getAllByRole('columnheader').map((header) => header.textContent);
    const costIndex = headers.indexOf('Costo');
    const row = screen.getByRole('row', { name: /iPhone 12/ });
    const cells = row.querySelectorAll('td');

    expect(costIndex).toBeGreaterThanOrEqual(0);
    expect(cells[costIndex]).toHaveTextContent('—');
  });

  it('calls onEdit with the product when Editar is clicked', async () => {
    const onEdit = vi.fn();
    renderList({ onEdit });

    await userEvent.click(screen.getByRole('button', { name: 'Editar' }));

    expect(onEdit).toHaveBeenCalledWith(products[0]);
  });

  it('calls onDeactivate with the product id when Desactivar is clicked', async () => {
    const onDeactivate = vi.fn();
    renderList({ onDeactivate });

    await userEvent.click(screen.getByRole('button', { name: 'Desactivar' }));

    expect(onDeactivate).toHaveBeenCalledWith(1);
  });

  it('shows Reactivar instead of Desactivar for an inactive product', () => {
    renderList({ products: [{ ...products[0], active: false }] });

    expect(screen.queryByRole('button', { name: 'Desactivar' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reactivar' })).toBeInTheDocument();
  });

  it('calls onActivate with the product id when Reactivar is clicked', async () => {
    const onActivate = vi.fn();
    renderList({ products: [{ ...products[0], active: false }], onActivate });

    await userEvent.click(screen.getByRole('button', { name: 'Reactivar' }));

    expect(onActivate).toHaveBeenCalledWith(1);
  });
});
