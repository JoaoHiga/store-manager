import { useState } from 'react';
import { Button } from '../atoms/Button';
import { FormField } from '../molecules/FormField';

const EMPTY_LINE = { productId: '', quantity: '', unitCost: '' };

const UNIT_COST_PATTERN = /^\d+(\.\d{1,2})?$/;

/** Line subtotal in cents, so the running total does not accumulate floating-point error. */
function lineSubtotalCents(line) {
  if (!/^\d+$/.test(line.quantity) || !UNIT_COST_PATTERN.test(line.unitCost)) {
    return 0;
  }
  return Number(line.quantity) * Math.round(Number(line.unitCost) * 100);
}

function validate(values, lines) {
  if (!values.supplierId) return 'Seleccione un proveedor';
  if (!values.invoiceNumber.trim()) return 'El número de factura es obligatorio';
  if (!values.purchaseDate) return 'La fecha es obligatoria';
  if (lines.some((line) => !line.productId)) return 'Seleccione un producto en cada línea';
  if (lines.some((line) => !/^\d+$/.test(line.quantity) || Number(line.quantity) <= 0)) {
    return 'La cantidad debe ser un número entero mayor a cero en cada línea';
  }
  if (lines.some((line) => !UNIT_COST_PATTERN.test(line.unitCost))) {
    return 'El costo unitario debe ser cero o mayor, con hasta 2 decimales, en cada línea';
  }
  if (new Set(lines.map((line) => line.productId)).size !== lines.length) {
    return 'Un producto no puede repetirse en dos líneas';
  }
  return null;
}

/**
 * Presentational record-purchase form: supplier, supplier invoice number, date and one or more
 * lines (product, quantity, unit cost). The page passes only active suppliers and products; the
 * backend still owns those rules, the future-date rule and invoice uniqueness, and its error is
 * shown by the page. The page remounts this form (via `key`) to clear it after a successful record.
 * `noValidate` so these messages, not the browser's `min`/`step` checks, explain a rejected line.
 */
export function PurchaseForm({ suppliers, products, today, onSubmit, isSubmitting = false }) {
  const [values, setValues] = useState({ supplierId: '', invoiceNumber: '', purchaseDate: today });
  const [lines, setLines] = useState([{ ...EMPTY_LINE }]);
  const [error, setError] = useState(null);

  const totalCents = lines.reduce((sum, line) => sum + lineSubtotalCents(line), 0);

  function handleChange(field) {
    return (event) => setValues((current) => ({ ...current, [field]: event.target.value }));
  }

  function updateLine(index, field, value) {
    setLines((current) => current.map((line, i) => (i === index ? { ...line, [field]: value } : line)));
  }

  function addLine() {
    setLines((current) => [...current, { ...EMPTY_LINE }]);
  }

  function removeLine(index) {
    setLines((current) => current.filter((_, i) => i !== index));
  }

  function handleSubmit(event) {
    event.preventDefault();
    const validationError = validate(values, lines);
    if (validationError) {
      setError(validationError);
      return;
    }
    setError(null);
    onSubmit({
      supplierId: Number(values.supplierId),
      invoiceNumber: values.invoiceNumber.trim(),
      purchaseDate: values.purchaseDate,
      items: lines.map((line) => ({
        productId: Number(line.productId),
        quantity: Number(line.quantity),
        unitCost: Number(line.unitCost),
      })),
    });
  }

  return (
    <form onSubmit={handleSubmit} className="sale-form" noValidate>
      <div className="form-field">
        <label htmlFor="purchase-supplier">Proveedor</label>
        <select id="purchase-supplier" value={values.supplierId} onChange={handleChange('supplierId')}>
          <option value="">Seleccione un proveedor</option>
          {suppliers.map((supplier) => (
            <option key={supplier.id} value={supplier.id}>
              {supplier.name}
            </option>
          ))}
        </select>
      </div>
      <FormField
        id="purchase-invoice-number"
        label="Número de factura"
        maxLength={30}
        value={values.invoiceNumber}
        onChange={handleChange('invoiceNumber')}
      />
      <FormField
        id="purchase-date"
        label="Fecha"
        type="date"
        max={today}
        value={values.purchaseDate}
        onChange={handleChange('purchaseDate')}
      />

      {lines.map((line, index) => (
        <div className="sale-form-line" key={index}>
          <div className="form-field">
            <label htmlFor={`purchase-product-${index}`}>Producto</label>
            <select
              id={`purchase-product-${index}`}
              value={line.productId}
              onChange={(event) => updateLine(index, 'productId', event.target.value)}
            >
              <option value="">Seleccione un producto</option>
              {products.map((product) => (
                <option key={product.id} value={product.id}>
                  {product.name}
                </option>
              ))}
            </select>
          </div>
          <FormField
            id={`purchase-quantity-${index}`}
            label="Cantidad"
            type="number"
            min="1"
            step="1"
            value={line.quantity}
            onChange={(event) => updateLine(index, 'quantity', event.target.value)}
          />
          <FormField
            id={`purchase-unit-cost-${index}`}
            label="Costo unitario"
            type="number"
            min="0"
            step="0.01"
            value={line.unitCost}
            onChange={(event) => updateLine(index, 'unitCost', event.target.value)}
          />
          {lines.length > 1 && (
            <Button type="button" variant="secondary" onClick={() => removeLine(index)}>
              Quitar línea
            </Button>
          )}
        </div>
      ))}
      <Button type="button" variant="secondary" onClick={addLine}>
        Agregar línea
      </Button>

      <p>Total: S/ {(totalCents / 100).toFixed(2)}</p>
      {error && <p role="alert">{error}</p>}
      <Button type="submit" disabled={isSubmitting}>
        Registrar compra
      </Button>
    </form>
  );
}
