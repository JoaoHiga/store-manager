import { Badge } from '../atoms/Badge';
import { Button } from '../atoms/Button';

export function SupplierList({ suppliers, onDeactivate, onActivate }) {
  if (suppliers.length === 0) {
    return <p>No hay proveedores todavía.</p>;
  }

  return (
    <table className="product-list">
      <thead>
        <tr>
          <th>Nombre</th>
          <th>RUC</th>
          <th>Teléfono</th>
          <th>Correo</th>
          <th>Estado</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        {suppliers.map((supplier) => (
          <tr key={supplier.id}>
            <td>{supplier.name}</td>
            <td>{supplier.ruc}</td>
            <td>{supplier.phone}</td>
            <td>{supplier.email}</td>
            <td>
              <Badge active={supplier.active} />
            </td>
            <td>
              {supplier.active ? (
                <Button variant="danger" onClick={() => onDeactivate(supplier.id)}>
                  Desactivar
                </Button>
              ) : (
                <Button variant="primary" onClick={() => onActivate(supplier.id)}>
                  Reactivar
                </Button>
              )}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
