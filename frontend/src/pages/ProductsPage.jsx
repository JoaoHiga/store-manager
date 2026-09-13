import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import {
  activateProduct,
  createProduct,
  deactivateProduct,
  listCategories,
  listProducts,
  updateProduct,
} from '../api/catalogApi';
import { ProductForm } from '../components/organisms/ProductForm';
import { ProductList } from '../components/organisms/ProductList';

export function ProductsPage() {
  const queryClient = useQueryClient();
  const { data: categories = [] } = useQuery({ queryKey: ['categories'], queryFn: listCategories });

  const [showInactive, setShowInactive] = useState(false);
  const {
    data: products = [],
    isLoading,
    error,
  } = useQuery({
    queryKey: ['products', showInactive],
    queryFn: () => listProducts({ active: !showInactive }),
  });

  const [editingProduct, setEditingProduct] = useState(null);
  const [mutationError, setMutationError] = useState(null);

  function invalidateProducts() {
    // Partial key: invalidates both the active and inactive list variants.
    queryClient.invalidateQueries({ queryKey: ['products'] });
  }

  function handleMutationSuccess() {
    invalidateProducts();
    setMutationError(null);
  }

  function handleMutationError(mutationErr) {
    setMutationError(mutationErr.message);
  }

  const createMutation = useMutation({
    mutationFn: (product) => createProduct(product),
    onSuccess: handleMutationSuccess,
    onError: handleMutationError,
  });
  const updateMutation = useMutation({
    mutationFn: ({ id, product }) => updateProduct(id, product),
    onSuccess: () => {
      handleMutationSuccess();
      setEditingProduct(null);
    },
    onError: handleMutationError,
  });
  const deactivateMutation = useMutation({
    mutationFn: (id) => deactivateProduct(id),
    onSuccess: handleMutationSuccess,
    onError: handleMutationError,
  });
  const activateMutation = useMutation({
    mutationFn: (id) => activateProduct(id),
    onSuccess: handleMutationSuccess,
    onError: handleMutationError,
  });

  function handleSubmit(values) {
    if (editingProduct) {
      updateMutation.mutate({ id: editingProduct.id, product: values });
    } else {
      createMutation.mutate(values);
    }
  }

  if (isLoading) return <p>Cargando productos...</p>;
  if (error) return <p role="alert">Error al cargar productos: {error.message}</p>;

  return (
    <section>
      <h2>Productos</h2>
      <ProductForm
        // Remounts the form when switching between create/edit (or between
        // two different products to edit): ProductForm reads `initialValues`
        // only once via useState, so without a key change React would keep
        // showing stale values from the previous render instead of the
        // product just clicked.
        key={editingProduct?.id ?? 'new'}
        categories={categories}
        initialValues={
          editingProduct ? { ...editingProduct, categoryId: String(editingProduct.categoryId) } : undefined
        }
        onSubmit={handleSubmit}
        onCancel={editingProduct ? () => setEditingProduct(null) : undefined}
      />
      {mutationError && <p role="alert">{mutationError}</p>}
      <label>
        <input
          type="checkbox"
          checked={showInactive}
          onChange={(event) => setShowInactive(event.target.checked)}
        />
        Ver productos inactivos
      </label>
      <ProductList
        products={products}
        categories={categories}
        onEdit={setEditingProduct}
        onDeactivate={(id) => deactivateMutation.mutate(id)}
        onActivate={(id) => activateMutation.mutate(id)}
      />
    </section>
  );
}
