import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { listProducts } from '../api/catalogApi';
import { listPurchases, listSuppliers, recordPurchase } from '../api/purchasingApi';
import { PurchaseForm } from '../components/organisms/PurchaseForm';
import { PurchaseList } from '../components/organisms/PurchaseList';

/** Today in the store's time zone as `YYYY-MM-DD`: the backend rejects a purchase dated after it. */
function storeToday() {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima' }).format(new Date());
}

/**
 * Records a purchase from a supplier and lists past purchases. Only active suppliers and products
 * are offered, since the backend rejects inactive ones. If either list fails to load, the form is
 * replaced by the error: its selects could not be filled.
 */
export function PurchasesPage() {
  const queryClient = useQueryClient();
  const { data: suppliers = [], error: suppliersError } = useQuery({
    queryKey: ['suppliers', { active: true }],
    queryFn: () => listSuppliers({ active: true }),
  });
  const { data: products = [], error: productsError } = useQuery({
    queryKey: ['products', { active: true }],
    queryFn: () => listProducts({ active: true }),
  });
  const { data: purchases = [], isLoading, error } = useQuery({ queryKey: ['purchases'], queryFn: listPurchases });

  const [mutationError, setMutationError] = useState(null);
  const [successMessage, setSuccessMessage] = useState(null);
  // Bumped after a successful record to remount (and so clear) the form.
  const [formKey, setFormKey] = useState(0);

  const recordMutation = useMutation({
    mutationFn: (purchase) => recordPurchase(purchase),
    onSuccess: (purchase) => {
      queryClient.invalidateQueries({ queryKey: ['purchases'] });
      // Stock and cost price changed.
      queryClient.invalidateQueries({ queryKey: ['products'] });
      setMutationError(null);
      setSuccessMessage(`Compra #${purchase.id} registrada correctamente`);
      setFormKey((key) => key + 1);
    },
    onError: (mutationErr) => {
      setSuccessMessage(null);
      setMutationError(mutationErr.message);
    },
  });

  return (
    <section>
      <h2>Compras</h2>
      {suppliersError && <p role="alert">Error al cargar proveedores: {suppliersError.message}</p>}
      {productsError && <p role="alert">Error al cargar productos: {productsError.message}</p>}
      {!suppliersError && !productsError && (
        <PurchaseForm
          key={formKey}
          suppliers={suppliers}
          products={products}
          today={storeToday()}
          onSubmit={(purchase) => recordMutation.mutate(purchase)}
          isSubmitting={recordMutation.isPending}
        />
      )}
      {mutationError && <p role="alert">{mutationError}</p>}
      {successMessage && <p role="status">{successMessage}</p>}
      {isLoading && <p>Cargando compras...</p>}
      {error && <p role="alert">Error al cargar compras: {error.message}</p>}
      {!isLoading && !error && <PurchaseList purchases={purchases} />}
    </section>
  );
}
