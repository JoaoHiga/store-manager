import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { FormField } from './FormField';

describe('FormField', () => {
  it('links the label to the input via htmlFor/id', () => {
    render(<FormField id="name" label="Nombre" value="iPhone 12" onChange={() => {}} />);

    expect(screen.getByLabelText('Nombre')).toHaveValue('iPhone 12');
  });

  it('shows the error message when one is provided', () => {
    render(<FormField id="name" label="Nombre" error="El nombre es obligatorio" onChange={() => {}} />);

    expect(screen.getByRole('alert')).toHaveTextContent('El nombre es obligatorio');
  });

  it('renders no error element when none is provided', () => {
    render(<FormField id="name" label="Nombre" onChange={() => {}} />);

    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
