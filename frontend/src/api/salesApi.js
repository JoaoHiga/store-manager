// Thin fetch wrapper over the store-manager sales REST API. Deliberately no
// TanStack Query in here: this module is the plain data layer; the
// useQuery/useMutation wiring belongs in the pages/organisms that call it,
// per design.md (mirrors catalogApi.js's shape exactly).

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

export function listSales() {
  return request('/api/sales');
}

export function getSale(id) {
  return request(`/api/sales/${id}`);
}

export function recordSale({ items, buyerDocumentType, buyerDocumentNumber, voucherType }) {
  return request('/api/sales', {
    method: 'POST',
    body: JSON.stringify({ items, buyerDocumentType, buyerDocumentNumber, voucherType }),
  });
}

export function retryVoucher(id) {
  return request(`/api/sales/${id}/retry-voucher`, {
    method: 'POST',
  });
}

export function voidSale(id, reason) {
  return request(`/api/sales/${id}/void`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  });
}
