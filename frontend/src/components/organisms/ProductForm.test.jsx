import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ProductForm } from './ProductForm';

const categories = [{ id: 1, name: 'Celulares' }];

describe('ProductForm', () => {
  it('submits the parsed values on a valid form', async () => {
    const onSubmit = vi.fn();
    render(<ProductForm categories={categories} onSubmit={onSubmit} />);

    await userEvent.type(screen.getByLabelText('Nombre'), 'iPhone 12');
    await userEvent.selectOptions(screen.getByLabelText('Categoría'), '1');
    await userEvent.type(screen.getByLabelText('Precio de venta'), '350');
    await userEvent.type(screen.getByLabelText('Stock'), '3');
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }));

    expect(onSubmit).toHaveBeenCalledWith({
      name: 'iPhone 12',
      description: null,
      categoryId: 1,
      brand: null,
      costPrice: null,
      salePrice: 350,
      stockQuantity: 3,
    });
  });

  it('rejects a negative stock quantity without calling onSubmit', async () => {
    const onSubmit = vi.fn();
    render(<ProductForm categories={categories} onSubmit={onSubmit} />);

    await userEvent.type(screen.getByLabelText('Nombre'), 'iPhone 12');
    await userEvent.selectOptions(screen.getByLabelText('Categoría'), '1');
    await userEvent.type(screen.getByLabelText('Precio de venta'), '350');
    await userEvent.type(screen.getByLabelText('Stock'), '-1');
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }));

    expect(onSubmit).not.toHaveBeenCalled();
    expect(screen.getByRole('alert')).toHaveTextContent('El stock no puede ser negativo');
  });

  it('does not crash on a non-numeric sale price and still rejects it', async () => {
    // Regression guard for a claimed NaN gap: type="number" inputs sanitize
    // a non-numeric typed value to "" in the DOM before any JS validation
    // runs, so the existing !values.salePrice check already catches this —
    // no source change was needed, this just proves it stays that way.
    const onSubmit = vi.fn();
    render(<ProductForm categories={categories} onSubmit={onSubmit} />);

    await userEvent.type(screen.getByLabelText('Nombre'), 'iPhone 12');
    await userEvent.selectOptions(screen.getByLabelText('Categoría'), '1');
    fireEvent.change(screen.getByLabelText('Precio de venta'), { target: { value: 'abc' } });
    await userEvent.type(screen.getByLabelText('Stock'), '3');
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }));

    expect(onSubmit).not.toHaveBeenCalled();
    expect(screen.getByRole('alert')).toHaveTextContent('El precio de venta tiene que ser mayor a cero');
  });

  it('pre-fills the form and shows Cancelar when editing', () => {
    render(
      <ProductForm
        categories={categories}
        initialValues={{
          name: 'iPhone 12',
          description: '',
          categoryId: '1',
          brand: '',
          costPrice: '',
          salePrice: '350',
          stockQuantity: '3',
        }}
        onSubmit={vi.fn()}
        onCancel={vi.fn()}
      />,
    );

    expect(screen.getByLabelText('Nombre')).toHaveValue('iPhone 12');
    expect(screen.getByRole('button', { name: 'Cancelar' })).toBeInTheDocument();
  });

  it('neither shows nor submits stock when editing', async () => {
    const onSubmit = vi.fn();
    render(
      <ProductForm
        categories={categories}
        initialValues={{
          name: 'iPhone 12',
          description: null,
          categoryId: '1',
          brand: null,
          costPrice: null,
          salePrice: 350,
          stockQuantity: 3,
        }}
        onSubmit={onSubmit}
        onCancel={vi.fn()}
      />,
    );

    expect(screen.queryByLabelText('Stock')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }));

    expect(onSubmit).toHaveBeenCalledWith({
      name: 'iPhone 12',
      description: null,
      categoryId: 1,
      brand: null,
      costPrice: null,
      salePrice: 350,
    });
  });
});
