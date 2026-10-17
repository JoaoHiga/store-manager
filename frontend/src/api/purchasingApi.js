// Thin fetch wrapper over the store-manager purchasing REST API. Deliberately no
// TanStack Query in here: this module is the plain data layer; the
// useQuery/useMutation wiring belongs in the pages that call it
// (mirrors catalogApi.js's shape exactly).

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: { 'Content-Type': 'application/json', ...options.headers },
    ...options,
  });

  if (!response.ok) {
    const body = await response.json().catch(() => null);
    const message = body?.message ?? `Request to ${path} failed with status ${response.status}`;
    throw new Error(message);
  }

  if (response.status === 204) {
    return null;
  }

  return response.json();
}

/** Without `active` the backend returns every supplier; with it, only that set. Ordered by name. */
export function listSuppliers({ active } = {}) {
  const query = active !== undefined ? `?active=${active}` : '';
  return request(`/api/suppliers${query}`);
}

export function createSupplier(supplier) {
  return request('/api/suppliers', {
    method: 'POST',
    body: JSON.stringify(supplier),
  });
}

export function deactivateSupplier(id) {
  return request(`/api/suppliers/${id}/deactivate`, {
    method: 'PATCH',
  });
}

export function activateSupplier(id) {
  return request(`/api/suppliers/${id}/activate`, {
    method: 'PATCH',
  });
}

/** The backend rejects an inactive supplier/product (400), an unknown one (404) and a duplicate supplier invoice (409). */
export function recordPurchase(purchase) {
  return request('/api/purchases', {
    method: 'POST',
    body: JSON.stringify(purchase),
  });
}

/** Summaries (no lines), newest first. */
export function listPurchases() {
  return request('/api/purchases');
}

export function getPurchase(id) {
  return request(`/api/purchases/${id}`);
}
