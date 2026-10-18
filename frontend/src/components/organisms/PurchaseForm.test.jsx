import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { PurchaseForm } from './PurchaseForm';

const suppliers = [{ id: 1, name: 'Distribuidora Lima', active: true }];
const products = [
  { id: 5, name: 'iPhone 12', active: true },
  { id: 6, name: 'Funda', active: true },
];

function renderForm(props = {}) {
  const onSubmit = vi.fn();
  render(<PurchaseForm suppliers={suppliers} products={products} today="2026-10-17" onSubmit={onSubmit} {...props} />);
  return onSubmit;
}

async function fillLine(index, { productId, quantity, unitCost }) {
  if (productId) await userEvent.selectOptions(screen.getAllByLabelText('Producto')[index], String(productId));
  if (quantity) await userEvent.type(screen.getAllByLabelText('Cantidad')[index], quantity);
  if (unitCost) await userEvent.type(screen.getAllByLabelText('Costo unitario')[index], unitCost);
}

async function fillHeader({ supplierId = 1, invoiceNumber = 'F001-123' } = {}) {
  if (supplierId) await userEvent.selectOptions(screen.getByLabelText('Proveedor'), String(supplierId));
  if (invoiceNumber) await userEvent.type(screen.getByLabelText('Número de factura'), invoiceNumber);
}

describe('PurchaseForm', () => {
  it('submits the header and every line, defaulting the date to today', async () => {
    const onSubmit = renderForm();

    await fillHeader({ invoiceNumber: '  F001-123  ' });
    await fillLine(0, { productId: 5, quantity: '2', unitCost: '330' });
    await userEvent.click(screen.getByRole('button', { name: 'Agregar línea' }));
    await fillLine(1, { productId: 6, quantity: '10', unitCost: '4.50' });
    await userEvent.click(screen.getByRole('button', { name: 'Registrar compra' }));

    expect(onSubmit).toHaveBeenCalledWith({
      supplierId: 1,
      invoiceNumber: 'F001-123',
      purchaseDate: '2026-10-17',
      items: [
        { productId: 5, quantity: 2, unitCost: 330 },
        { productId: 6, quantity: 10, unitCost: 4.5 },
      ],
    });
  });

  it('shows the running total of the lines', async () => {
    renderForm();

    await fillLine(0, { productId: 5, quantity: '2', unitCost: '330' });
    await userEvent.click(screen.getByRole('button', { name: 'Agregar línea' }));
    await fillLine(1, { productId: 6, quantity: '10', unitCost: '4.50' });

    expect(screen.getByText('Total: S/ 705.00')).toBeInTheDocument();
  });

  it('removes a line', async () => {
    renderForm();

    await userEvent.click(screen.getByRole('button', { name: 'Agregar línea' }));
    expect(screen.getAllByLabelText('Producto')).toHaveLength(2);
    await userEvent.click(screen.getAllByRole('button', { name: 'Quitar línea' })[1]);

    expect(screen.getAllByLabelText('Producto')).toHaveLength(1);
    expect(screen.queryByRole('button', { name: 'Quitar línea' })).not.toBeInTheDocument();
  });

  it('removes a middle line, keeping the other lines and updating the total', async () => {
    renderForm({
      products: [...products, { id: 7, name: 'Mica templada', active: true }],
    });

    await fillLine(0, { productId: 5, quantity: '2', unitCost: '330' });
    await userEvent.click(screen.getByRole('button', { name: 'Agregar línea' }));
    await fillLine(1, { productId: 6, quantity: '10', unitCost: '4.50' });
    await userEvent.click(screen.getByRole('button', { name: 'Agregar línea' }));
    await fillLine(2, { productId: 7, quantity: '3', unitCost: '5' });
    expect(screen.getByText('Total: S/ 720.00')).toBeInTheDocument();

    await userEvent.click(screen.getAllByRole('button', { name: 'Quitar línea' })[1]);

    expect(screen.getAllByLabelText('Producto').map((select) => select.value)).toEqual(['5', '7']);
    expect(screen.getAllByLabelText('Cantidad').map((input) => input.value)).toEqual(['2', '3']);
    expect(screen.getAllByLabelText('Costo unitario').map((input) => input.value)).toEqual(['330', '5']);
    expect(screen.getByText('Total: S/ 675.00')).toBeInTheDocument();
  });

  it.each([
    ['a missing supplier', { supplierId: null }, {}, 'Seleccione un proveedor'],
    ['a blank invoice number', { invoiceNumber: '   ' }, {}, 'El número de factura es obligatorio'],
    ['a line without a product', {}, { productId: null }, 'Seleccione un producto en cada línea'],
    ['a zero quantity', {}, { quantity: '0' }, 'La cantidad debe ser un número entero mayor a cero en cada línea'],
    [
      'a fractional quantity',
      {},
      { quantity: '1.5' },
      'La cantidad debe ser un número entero mayor a cero en cada línea',
    ],
    ['a negative quantity', {}, { quantity: '-2' }, 'La cantidad debe ser un número entero mayor a cero en cada línea'],
    [
      'a unit cost with more than 2 decimals',
      {},
      { unitCost: '1.005' },
      'El costo unitario debe ser cero o mayor, con hasta 2 decimales, en cada línea',
    ],
  ])('rejects %s without calling onSubmit', async (_, header, line, message) => {
    const onSubmit = renderForm();

    await fillHeader(header);
    await fillLine(0, { productId: 5, quantity: '1', unitCost: '10', ...line });
    await userEvent.click(screen.getByRole('button', { name: 'Registrar compra' }));

    expect(onSubmit).not.toHaveBeenCalled();
    expect(screen.getByRole('alert')).toHaveTextContent(message);
  });

  it('rejects the same product on two lines', async () => {
    const onSubmit = renderForm();

    await fillHeader();
    await fillLine(0, { productId: 5, quantity: '1', unitCost: '10' });
    await userEvent.click(screen.getByRole('button', { name: 'Agregar línea' }));
    await fillLine(1, { productId: 5, quantity: '1', unitCost: '10' });
    await userEvent.click(screen.getByRole('button', { name: 'Registrar compra' }));

    expect(onSubmit).not.toHaveBeenCalled();
    expect(screen.getByRole('alert')).toHaveTextContent('Un producto no puede repetirse en dos líneas');
  });

  it('disables the submit button while submitting', () => {
    renderForm({ isSubmitting: true });

    expect(screen.getByRole('button', { name: 'Registrar compra' })).toBeDisabled();
  });
});
