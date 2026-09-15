import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { listSales, retryVoucher, voidSale } from '../api/salesApi';
import { Badge } from '../components/atoms/Badge';
import { Button } from '../components/atoms/Button';
import { PriceTag } from '../components/molecules/PriceTag';

const VOUCHER_STATUS_LABELS = {
  PENDING: 'Pendiente',
  ACCEPTED: 'Aceptado',
  NEEDS_ATTENTION: 'Requiere atención',
  VOIDED: 'Anulado',
};

const VOUCHER_STATUS_TONES = {
  PENDING: 'neutral',
  ACCEPTED: 'positive',
  NEEDS_ATTENTION: 'warning',
  VOIDED: 'negative',
};

/**
 * `GET /api/sales` correctly returns the real `voucherStatus` (verified by
 * `SaleFlowE2ETest` and the Playwright E2E suite, automated-e2e-testing).
 * `null`/`undefined` is still handled defensively as "unknown" rather than
 * a status of its own — the retry/void buttons stay visible in that case
 * (the backend still enforces the real rule on every call) and the badge
 * says so plainly instead of guessing — but this path shouldn't be reached
 * in practice, since a voucher is always created synchronously when a sale
 * is recorded.
 */
function voucherBadge(voucherStatus) {
  if (!voucherStatus) {
    return <Badge tone="neutral">Desconocido</Badge>;
  }
  return (
    <Badge tone={VOUCHER_STATUS_TONES[voucherStatus] ?? 'neutral'}>
      {VOUCHER_STATUS_LABELS[voucherStatus] ?? voucherStatus}
    </Badge>
  );
}

export function SalesListPage() {
  const queryClient = useQueryClient();
  const {
    data: sales = [],
    isLoading,
    error,
  } = useQuery({ queryKey: ['sales'], queryFn: listSales });
  const [actionError, setActionError] = useState(null);

  // Patches the cached list directly instead of invalidating it: re-fetching
  // `GET /api/sales` would just overwrite the status we just learned with
  // `null` again (see the module doc above), erasing the one accurate
  // voucherStatus we have for this sale.
  function patchSale(id, patch) {
    queryClient.setQueryData(['sales'], (current = []) =>
      current.map((sale) => (sale.id === id ? { ...sale, ...patch } : sale)),
    );
  }

  const retryMutation = useMutation({
    mutationFn: (id) => retryVoucher(id),
    onSuccess: (voucher, id) => {
      setActionError(null);
      patchSale(id, { voucherStatus: voucher.status });
    },
    onError: (mutationError) => setActionError(mutationError.message),
  });

  const voidMutation = useMutation({
    mutationFn: ({ id, reason }) => voidSale(id, reason),
    onSuccess: (sale, { id }) => {
      setActionError(null);
      // The void endpoint only ever succeeds by transitioning ACCEPTED -> VOIDED,
      // so this is safe to assume even though SaleResponse itself omits it.
      patchSale(id, { status: sale.status, voucherStatus: 'VOIDED' });
    },
    onError: (mutationError) => setActionError(mutationError.message),
  });

  function handleVoid(sale) {
    const reason = window.prompt('Motivo de la anulación:');
    if (!reason || !reason.trim()) return;
    voidMutation.mutate({ id: sale.id, reason });
  }

  if (isLoading) return <p>Cargando ventas...</p>;
  if (error) return <p role="alert">Error al cargar ventas: {error.message}</p>;

  return (
    <section>
      <h2>Ventas</h2>
      {actionError && <p role="alert">{actionError}</p>}
      {sales.length === 0 ? (
        <p>No hay ventas todavía.</p>
      ) : (
        <table className="sale-list">
          <thead>
            <tr>
              <th>ID</th>
              <th>Documento</th>
              <th>Total</th>
              <th>Estado</th>
              <th>Comprobante</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {sales.map((sale) => {
              const voidedOrCancelled = sale.status === 'CANCELLED' || sale.voucherStatus === 'VOIDED';
              const showRetry = !voidedOrCancelled && sale.voucherStatus !== 'ACCEPTED';
              const showVoid =
                !voidedOrCancelled &&
                sale.voucherStatus !== 'PENDING' &&
                sale.voucherStatus !== 'NEEDS_ATTENTION';

              return (
                <tr key={sale.id}>
                  <td>{sale.id}</td>
                  <td>
                    {sale.buyerDocumentType} {sale.buyerDocumentNumber}
                  </td>
                  <td>
                    <PriceTag amount={sale.total} />
                  </td>
                  <td>{sale.status}</td>
                  <td>{voucherBadge(sale.voucherStatus)}</td>
                  <td>
                    {showRetry && (
                      <Button
                        variant="secondary"
                        onClick={() => retryMutation.mutate(sale.id)}
                        disabled={retryMutation.isPending}
                      >
                        Reintentar
                      </Button>
                    )}
                    {showVoid && (
                      <Button variant="danger" onClick={() => handleVoid(sale)} disabled={voidMutation.isPending}>
                        Anular
                      </Button>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
    </section>
  );
}
