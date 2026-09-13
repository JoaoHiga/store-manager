/** Renders a product's `active` state (see the soft-delete design decision). */
export function Badge({ active }) {
  return (
    <span className={`badge ${active ? 'badge-active' : 'badge-inactive'}`}>
      {active ? 'Activo' : 'Inactivo'}
    </span>
  );
}
