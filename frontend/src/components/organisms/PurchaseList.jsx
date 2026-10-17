export function PurchaseList({ purchases }) {
  if (purchases.length === 0) {
    return <p>No hay compras todavía.</p>;
  }

  return (
    <table className="sale-list">
      <thead>
        <tr>
          <th>Fecha</th>
          <th>Proveedor</th>
          <th>Factura</th>
          <th>Líneas</th>
          <th>Total</th>
        </tr>
      </thead>
      <tbody>
        {purchases.map((purchase) => (
          <tr key={purchase.id}>
            <td>{purchase.purchaseDate}</td>
            <td>{purchase.supplierName}</td>
            <td>{purchase.invoiceNumber}</td>
            <td>{purchase.lineCount}</td>
            <td>S/ {Number(purchase.total).toFixed(2)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
