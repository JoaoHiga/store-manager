import { Badge } from '../atoms/Badge';
import { Button } from '../atoms/Button';
import { PriceTag } from '../molecules/PriceTag';

export function ProductList({ products, categories, onEdit, onDeactivate }) {
  if (products.length === 0) {
    return <p>No hay productos todavía.</p>;
  }

  function categoryName(categoryId) {
    return categories.find((category) => category.id === categoryId)?.name ?? `Categoría #${categoryId}`;
  }

  return (
    <table className="product-list">
      <thead>
        <tr>
          <th>SKU</th>
          <th>Nombre</th>
          <th>Categoría</th>
          <th>Precio</th>
          <th>Stock</th>
          <th>Estado</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        {products.map((product) => (
          <tr key={product.id}>
            <td>{product.sku}</td>
            <td>{product.name}</td>
            <td>{categoryName(product.categoryId)}</td>
            <td>
              <PriceTag amount={product.salePrice} />
            </td>
            <td>{product.stockQuantity}</td>
            <td>
              <Badge active={product.active} />
            </td>
            <td>
              <Button variant="secondary" onClick={() => onEdit(product)}>
                Editar
              </Button>
              {product.active && (
                <Button variant="danger" onClick={() => onDeactivate(product.id)}>
                  Desactivar
                </Button>
              )}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
