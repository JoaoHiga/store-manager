/** Formats a product's costPrice/salePrice as currency. */
export function PriceTag({ amount, currency = 'USD' }) {
  const formatted = new Intl.NumberFormat('es-AR', { style: 'currency', currency }).format(amount);
  return <span className="price-tag">{formatted}</span>;
}
