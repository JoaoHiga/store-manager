const TONE_CLASSES = {
  positive: 'badge-active',
  negative: 'badge-inactive',
  warning: 'badge-warning',
  neutral: 'badge-neutral',
};

/**
 * Renders a product's `active` state (see the soft-delete design decision)
 * when given `active`, or an arbitrary `tone` + label (e.g. a voucher
 * status) when given `tone`/`children` instead — same visual language,
 * reused rather than duplicated for sales-invoicing's voucher badges.
 */
export function Badge({ active, tone, children }) {
  if (active !== undefined) {
    return (
      <span className={`badge ${active ? 'badge-active' : 'badge-inactive'}`}>
        {active ? 'Activo' : 'Inactivo'}
      </span>
    );
  }

  return <span className={`badge ${TONE_CLASSES[tone] ?? TONE_CLASSES.neutral}`}>{children}</span>;
}
