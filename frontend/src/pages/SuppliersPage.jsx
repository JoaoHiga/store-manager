import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { activateSupplier, createSupplier, deactivateSupplier, listSuppliers } from '../api/purchasingApi';
import { SupplierForm } from '../components/organisms/SupplierForm';
import { SupplierList } from '../components/organisms/SupplierList';

export function SuppliersPage() {
  const queryClient = useQueryClient();
  // Every supplier, active and inactive, so an inactive one can be reactivated from the same list.
  const {
    data: suppliers = [],
    isLoading,
    error,
  } = useQuery({ queryKey: ['suppliers'], queryFn: () => listSuppliers() });

  const [mutationError, setMutationError] = useState(null);
  // Bumped after a successful create to remount (and so clear) the form.
  const [formKey, setFormKey] = useState(0);

  function handleMutationSuccess() {
    queryClient.invalidateQueries({ queryKey: ['suppliers'] });
    setMutationError(null);
  }

  function handleMutationError(mutationErr) {
    setMutationError(mutationErr.message);
  }

  const createMutation = useMutation({
    mutationFn: (supplier) => createSupplier(supplier),
    onSuccess: () => {
      handleMutationSuccess();
      setFormKey((key) => key + 1);
    },
    onError: handleMutationError,
  });
  const deactivateMutation = useMutation({
    mutationFn: (id) => deactivateSupplier(id),
    onSuccess: handleMutationSuccess,
    onError: handleMutationError,
  });
  const activateMutation = useMutation({
    mutationFn: (id) => activateSupplier(id),
    onSuccess: handleMutationSuccess,
    onError: handleMutationError,
  });

  if (isLoading) return <p>Cargando proveedores...</p>;
  if (error) return <p role="alert">Error al cargar proveedores: {error.message}</p>;

  return (
    <section>
      <h2>Proveedores</h2>
      <SupplierForm
        key={formKey}
        onSubmit={(supplier) => createMutation.mutate(supplier)}
        isSubmitting={createMutation.isPending}
      />
      {mutationError && <p role="alert">{mutationError}</p>}
      <SupplierList
        suppliers={suppliers}
        onDeactivate={(id) => deactivateMutation.mutate(id)}
        onActivate={(id) => activateMutation.mutate(id)}
      />
    </section>
  );
}
