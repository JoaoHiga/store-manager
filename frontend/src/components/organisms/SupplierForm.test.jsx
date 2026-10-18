import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { SupplierForm } from './SupplierForm';

async function fillRequired({ name = 'Distribuidora Lima', ruc = '20131312955' } = {}) {
  if (name) await userEvent.type(screen.getByLabelText('Nombre'), name);
  if (ruc) await userEvent.type(screen.getByLabelText('RUC'), ruc);
}

describe('SupplierForm', () => {
  it('submits trimmed values with empty optional contact data as null', async () => {
    const onSubmit = vi.fn();
    render(<SupplierForm onSubmit={onSubmit} />);

    await fillRequired({ name: '  Distribuidora Lima  ' });
    await userEvent.type(screen.getByLabelText('Teléfono'), '987654321');
    await userEvent.click(screen.getByRole('button', { name: 'Crear proveedor' }));

    expect(onSubmit).toHaveBeenCalledWith({
      name: 'Distribuidora Lima',
      ruc: '20131312955',
      phone: '987654321',
      email: null,
      address: null,
    });
  });

  it('requires a name without calling onSubmit', async () => {
    const onSubmit = vi.fn();
    render(<SupplierForm onSubmit={onSubmit} />);

    await fillRequired({ name: '' });
    await userEvent.click(screen.getByRole('button', { name: 'Crear proveedor' }));

    expect(onSubmit).not.toHaveBeenCalled();
    expect(screen.getByRole('alert')).toHaveTextContent('El nombre es obligatorio');
  });

  it('rejects a RUC that is not 11 digits without calling onSubmit', async () => {
    const onSubmit = vi.fn();
    render(<SupplierForm onSubmit={onSubmit} />);

    await fillRequired({ ruc: '2013131295' });
    await userEvent.click(screen.getByRole('button', { name: 'Crear proveedor' }));

    expect(onSubmit).not.toHaveBeenCalled();
    expect(screen.getByRole('alert')).toHaveTextContent('El RUC debe tener 11 dígitos');
  });

  it('disables the submit button while submitting', () => {
    render(<SupplierForm onSubmit={vi.fn()} isSubmitting />);

    expect(screen.getByRole('button', { name: 'Crear proveedor' })).toBeDisabled();
  });
});
