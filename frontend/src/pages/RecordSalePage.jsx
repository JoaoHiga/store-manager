import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { listProducts } from '../api/catalogApi';
import { recordSale } from '../api/salesApi';
import { Button } from '../components/atoms/Button';
import { FormField } from '../components/molecules/FormField';

const emptyLine = { productId: '', quantity: '' };

const BUYER_DOCUMENT_TYPES = ['DNI', 'RUC', 'CE', 'PASAPORTE'];

// Peru's "boleta simple" threshold (research.md): below this total a sale may be recorded with
// no buyer document at all. Kept in sync with BuyerDocumentValidator.BOLETA_SIMPLE_THRESHOLD on
// the backend — the real enforcer; this is only a soft, client-side hint.
const BOLETA_SIMPLE_THRESHOLD = 700;

const DOCUMENT_FORMAT_HINTS = {
  DNI: 'DNI: 8 dígitos',
  RUC: 'RUC: 11 dígitos',
};

/**
 * Records a sale: one or more line items (product + quantity) plus a buyer
 * reference (documentType/documentNumber, per the `sales` spec). The product
 * picker only lists active products with stock available — an inactive or
 * out-of-stock product can never be sold, so it's excluded up front rather
 * than left for the backend's `InactiveProductException`/
 * `InsufficientStockException` to reject.
 */
export function RecordSalePage() {
  const queryClient = useQueryClient();
  const { data: products = [] } = useQuery({
    queryKey: ['products', { active: true }],
    queryFn: () => listProducts({ active: true }),
  });
  const availableProducts = products.filter((product) => product.active && product.stockQuantity > 0);

  const [lines, setLines] = useState([{ ...emptyLine }]);
  const [buyerDocumentType, setBuyerDocumentType] = useState(BUYER_DOCUMENT_TYPES[0]);
  const [buyerDocumentNumber, setBuyerDocumentNumber] = useState('');
  const [noDocument, setNoDocument] = useState(false);
  const [voucherType, setVoucherType] = useState('BOLETA');
  const [formError, setFormError] = useState(null);
  const [successMessage, setSuccessMessage] = useState(null);

  const isFactura = voucherType === 'FACTURA';

  function handleVoucherTypeChange(event) {
    const value = event.target.value;
    setVoucherType(value);
    if (value === 'FACTURA') {
      // Factura requires a RUC buyer (backend-enforced; see IssueVoucherForSaleUseCase) — force
      // it here too so the form never lets a user submit an invalid combination.
      setBuyerDocumentType('RUC');
      setNoDocument(false);
    }
  }

  const runningTotal = lines.reduce((sum, line) => {
    const product = availableProducts.find((candidate) => String(candidate.id) === String(line.productId));
    if (!product || line.quantity === '') {
      return sum;
    }
    return sum + product.salePrice * Number(line.quantity);
  }, 0);

  const recordMutation = useMutation({
    mutationFn: (payload) => recordSale(payload),
    onSuccess: (sale) => {
      queryClient.invalidateQueries({ queryKey: ['sales'] });
      queryClient.invalidateQueries({ queryKey: ['products'] });
      setFormError(null);
      setSuccessMessage(`Venta #${sale.id} registrada correctamente`);
      setLines([{ ...emptyLine }]);
      setBuyerDocumentNumber('');
      setNoDocument(false);
    },
    onError: (mutationError) => {
      setSuccessMessage(null);
      setFormError(mutationError.message);
    },
  });

  function updateLine(index, field, value) {
    setLines((prev) => prev.map((line, i) => (i === index ? { ...line, [field]: value } : line)));
  }

  function addLine() {
    setLines((prev) => [...prev, { ...emptyLine }]);
  }

  function removeLine(index) {
    setLines((prev) => prev.filter((_, i) => i !== index));
  }

  function handleSubmit(event) {
    event.preventDefault();
    setSuccessMessage(null);

    if (!noDocument && !buyerDocumentNumber.trim()) {
      setFormError('El número de documento es obligatorio');
      return;
    }
    if (lines.some((line) => !line.productId)) {
      setFormError('Seleccione un producto en cada línea');
      return;
    }
    if (lines.some((line) => line.quantity === '' || Number(line.quantity) <= 0)) {
      setFormError('La cantidad tiene que ser mayor a cero en cada línea');
      return;
    }
    const overStockedLine = lines.find((line) => {
      const product = availableProducts.find((candidate) => String(candidate.id) === String(line.productId));
      return product && Number(line.quantity) > product.stockQuantity;
    });
    if (overStockedLine) {
      setFormError('La cantidad supera el stock disponible de un producto');
      return;
    }

    setFormError(null);
    recordMutation.mutate({
      items: lines.map((line) => ({ productId: Number(line.productId), quantity: Number(line.quantity) })),
      ...(noDocument ? {} : { buyerDocumentType, buyerDocumentNumber }),
      voucherType,
    });
  }

  return (
    <section>
      <h2>Registrar venta</h2>
      <form onSubmit={handleSubmit} className="sale-form">
        {lines.map((line, index) => (
          <div className="sale-form-line" key={index}>
            <div className="form-field">
              <label htmlFor={`product-${index}`}>Producto</label>
              <select
                id={`product-${index}`}
                value={line.productId}
                onChange={(event) => updateLine(index, 'productId', event.target.value)}
              >
                <option value="">Seleccione un producto</option>
                {availableProducts.map((product) => (
                  <option key={product.id} value={product.id}>
                    {`${product.name} (${product.stockQuantity} en stock)`}
                  </option>
                ))}
              </select>
            </div>
            <FormField
              id={`quantity-${index}`}
              label="Cantidad"
              type="number"
              value={line.quantity}
              onChange={(event) => updateLine(index, 'quantity', event.target.value)}
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

        <div className="form-field">
          <label htmlFor="voucherType">Tipo de comprobante</label>
          <select id="voucherType" value={voucherType} onChange={handleVoucherTypeChange}>
            <option value="BOLETA">Boleta</option>
            <option value="FACTURA">Factura</option>
          </select>
        </div>

        <div className="form-field">
          <label htmlFor="noDocument">
            <input
              type="checkbox"
              id="noDocument"
              checked={noDocument}
              disabled={isFactura}
              onChange={(event) => setNoDocument(event.target.checked)}
            />
            {' '}Sin documento (boleta simple, ventas menores a S/ {BOLETA_SIMPLE_THRESHOLD})
          </label>
          {!noDocument && runningTotal > 0 && runningTotal < BOLETA_SIMPLE_THRESHOLD && (
            <p className="hint">Total actual: S/ {runningTotal.toFixed(2)} — esta venta califica para "sin documento".</p>
          )}
        </div>

        {!noDocument && (
          <>
            <div className="form-field">
              <label htmlFor="buyerDocumentType">Tipo de documento</label>
              <select
                id="buyerDocumentType"
                value={buyerDocumentType}
                disabled={isFactura}
                onChange={(event) => setBuyerDocumentType(event.target.value)}
              >
                {BUYER_DOCUMENT_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {type}
                  </option>
                ))}
              </select>
              {DOCUMENT_FORMAT_HINTS[buyerDocumentType] && (
                <p className="hint">{DOCUMENT_FORMAT_HINTS[buyerDocumentType]}</p>
              )}
            </div>
            <FormField
              id="buyerDocumentNumber"
              label="Número de documento"
              value={buyerDocumentNumber}
              onChange={(event) => setBuyerDocumentNumber(event.target.value)}
            />
          </>
        )}

        {formError && <p role="alert">{formError}</p>}
        {successMessage && <p role="status">{successMessage}</p>}

        <Button type="submit" disabled={recordMutation.isPending}>
          Registrar venta
        </Button>
      </form>
    </section>
  );
}
