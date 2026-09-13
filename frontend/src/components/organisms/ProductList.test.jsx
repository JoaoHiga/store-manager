import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ProductList } from './ProductList';

const categories = [{ id: 1, name: 'Celulares' }];
const products = [
  { id: 1, sku: 'PRD-000001', name: 'iPhone 12', categoryId: 1, salePrice: 350, stockQuantity: 3, active: true },
];

describe('ProductList', () => {
  it('shows a message when there are no products', () => {
    render(<ProductList products={[]} categories={categories} onEdit={vi.fn()} onDeactivate={vi.fn()} />);

    expect(screen.getByText('No hay productos todavía.')).toBeInTheDocument();
  });

  it('renders each product with its resolved category name', () => {
    render(<ProductList products={products} categories={categories} onEdit={vi.fn()} onDeactivate={vi.fn()} />);

    expect(screen.getByText('PRD-000001')).toBeInTheDocument();
    expect(screen.getByText('iPhone 12')).toBeInTheDocument();
    expect(screen.getByText('Celulares')).toBeInTheDocument();
  });

  it('calls onEdit with the product when Editar is clicked', async () => {
    const onEdit = vi.fn();
    render(<ProductList products={products} categories={categories} onEdit={onEdit} onDeactivate={vi.fn()} />);

    await userEvent.click(screen.getByRole('button', { name: 'Editar' }));

    expect(onEdit).toHaveBeenCalledWith(products[0]);
  });

  it('calls onDeactivate with the product id when Desactivar is clicked', async () => {
    const onDeactivate = vi.fn();
    render(<ProductList products={products} categories={categories} onEdit={vi.fn()} onDeactivate={onDeactivate} />);

    await userEvent.click(screen.getByRole('button', { name: 'Desactivar' }));

    expect(onDeactivate).toHaveBeenCalledWith(1);
  });

  it('hides the Desactivar button for an already-inactive product', () => {
    const inactive = [{ ...products[0], active: false }];
    render(<ProductList products={inactive} categories={categories} onEdit={vi.fn()} onDeactivate={vi.fn()} />);

    expect(screen.queryByRole('button', { name: 'Desactivar' })).not.toBeInTheDocument();
  });
});
