// Thin fetch wrapper over the store-manager REST API. Deliberately no
// TanStack Query in here: this module is the plain data layer; the
// useQuery/useMutation wiring belongs in the pages/organisms that call it,
// per design.md.

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

export function listCategories() {
  return request('/api/categories');
}

export function createCategory({ name, description }) {
  return request('/api/categories', {
    method: 'POST',
    body: JSON.stringify({ name, description }),
  });
}

export function listProducts({ categoryId } = {}) {
  const query = categoryId ? `?categoryId=${categoryId}` : '';
  return request(`/api/products${query}`);
}

export function createProduct(product) {
  return request('/api/products', {
    method: 'POST',
    body: JSON.stringify(product),
  });
}

export function updateProduct(id, product) {
  return request(`/api/products/${id}`, {
    method: 'PUT',
    body: JSON.stringify(product),
  });
}

export function deactivateProduct(id) {
  return request(`/api/products/${id}/deactivate`, {
    method: 'PATCH',
  });
}
