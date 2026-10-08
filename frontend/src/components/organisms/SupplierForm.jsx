import { useState } from 'react';
import { Button } from '../atoms/Button';
import { FormField } from '../molecules/FormField';

const EMPTY_VALUES = { name: '', ruc: '', phone: '', email: '', address: '' };

function blankToNull(value) {
  const trimmed = value.trim();
  return trimmed === '' ? null : trimmed;
}

/**
 * Presentational create-supplier form. Only checks the RUC's shape (11 digits); the backend owns
 * the check-digit and uniqueness rules and its error is shown by the page. The page remounts this
 * form (via `key`) to clear it after a successful create.
 */
export function SupplierForm({ onSubmit, isSubmitting = false }) {
  const [values, setValues] = useState(EMPTY_VALUES);
  const [error, setError] = useState(null);

  function handleChange(field) {
    return (event) => setValues((current) => ({ ...current, [field]: event.target.value }));
  }

  function handleSubmit(event) {
    event.preventDefault();
    if (!values.name.trim()) {
      setError('El nombre es obligatorio');
      return;
    }
    if (!/^\d{11}$/.test(values.ruc.trim())) {
      setError('El RUC debe tener 11 dígitos');
      return;
    }
    setError(null);
    onSubmit({
      name: values.name.trim(),
      ruc: values.ruc.trim(),
      phone: blankToNull(values.phone),
      email: blankToNull(values.email),
      address: blankToNull(values.address),
    });
  }

  return (
    <form onSubmit={handleSubmit}>
      <FormField id="supplier-name" label="Nombre" value={values.name} onChange={handleChange('name')} />
      <FormField id="supplier-ruc" label="RUC" inputMode="numeric" value={values.ruc} onChange={handleChange('ruc')} />
      <FormField id="supplier-phone" label="Teléfono" value={values.phone} onChange={handleChange('phone')} />
      <FormField
        id="supplier-email"
        label="Correo electrónico"
        type="email"
        value={values.email}
        onChange={handleChange('email')}
      />
      <FormField id="supplier-address" label="Dirección" value={values.address} onChange={handleChange('address')} />
      {error && <p role="alert">{error}</p>}
      <Button type="submit" disabled={isSubmitting}>
        Crear proveedor
      </Button>
    </form>
  );
}
