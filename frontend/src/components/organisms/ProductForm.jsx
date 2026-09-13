import { useState } from 'react';
import { Button } from '../atoms/Button';
import { FormField } from '../molecules/FormField';

const emptyForm = {
  name: '',
  description: '',
  categoryId: '',
  brand: '',
  costPrice: '',
  salePrice: '',
  stockQuantity: '',
};

/**
 * Handles both create (no `initialValues`) and edit. Deliberately has no
 * `sku` field: sku can never be set/changed from the UI, matching the
 * backend contract (see product-catalog spec.md).
 */
export function ProductForm({ categories, initialValues, onSubmit, onCancel }) {
  // Merge over emptyForm rather than using initialValues verbatim: a real
  // product from the API can have null optional fields (brand, description,
  // costPrice), and React warns on `value={null}` for a controlled input —
  // drop null/undefined entries so emptyForm's '' defaults apply instead.
  const definedInitialValues = Object.fromEntries(
    Object.entries(initialValues ?? {}).filter(([, value]) => value !== null && value !== undefined),
  );
  const [values, setValues] = useState({ ...emptyForm, ...definedInitialValues });
  const [error, setError] = useState(null);

  function handleChange(field) {
    return (event) => setValues((prev) => ({ ...prev, [field]: event.target.value }));
  }

  function handleSubmit(event) {
    event.preventDefault();
    setError(null);

    if (!values.name.trim()) {
      setError('El nombre es obligatorio');
      return;
    }
    if (!values.categoryId) {
      setError('Elegí una categoría');
      return;
    }
    if (!values.salePrice || Number(values.salePrice) <= 0) {
      setError('El precio de venta tiene que ser mayor a cero');
      return;
    }
    if (values.stockQuantity === '' || Number(values.stockQuantity) < 0) {
      setError('El stock no puede ser negativo');
      return;
    }

    onSubmit({
      name: values.name,
      description: values.description || null,
      categoryId: Number(values.categoryId),
      brand: values.brand || null,
      costPrice: values.costPrice === '' ? null : Number(values.costPrice),
      salePrice: Number(values.salePrice),
      stockQuantity: Number(values.stockQuantity),
    });
  }

  return (
    <form onSubmit={handleSubmit} className="product-form">
      <FormField id="name" label="Nombre" value={values.name} onChange={handleChange('name')} />
      <FormField
        id="description"
        label="Descripción"
        value={values.description}
        onChange={handleChange('description')}
      />
      <div className="form-field">
        <label htmlFor="categoryId">Categoría</label>
        <select id="categoryId" value={values.categoryId} onChange={handleChange('categoryId')}>
          <option value="">Elegí una categoría</option>
          {categories.map((category) => (
            <option key={category.id} value={category.id}>
              {category.name}
            </option>
          ))}
        </select>
      </div>
      <FormField id="brand" label="Marca" value={values.brand} onChange={handleChange('brand')} />
      <FormField
        id="costPrice"
        label="Precio de costo"
        type="number"
        step="0.01"
        value={values.costPrice}
        onChange={handleChange('costPrice')}
      />
      <FormField
        id="salePrice"
        label="Precio de venta"
        type="number"
        step="0.01"
        value={values.salePrice}
        onChange={handleChange('salePrice')}
      />
      <FormField
        id="stockQuantity"
        label="Stock"
        type="number"
        value={values.stockQuantity}
        onChange={handleChange('stockQuantity')}
      />
      {error && <p role="alert">{error}</p>}
      <Button type="submit">Guardar</Button>
      {onCancel && (
        <Button type="button" variant="secondary" onClick={onCancel}>
          Cancelar
        </Button>
      )}
    </form>
  );
}
