/** Coerce API BigDecimal / number / string to a finite number. */
export function toAmount(value) {
  if (value == null) return 0;
  const n = typeof value === 'number' ? value : parseFloat(value);
  return Number.isFinite(n) ? n : 0;
}

/** Format USD; shows 4 decimals when amount is under $0.01. */
export function formatCurrency(value) {
  const n = toAmount(value);
  if (n > 0 && n < 0.01) return `$${n.toFixed(4)}`;
  return `$${n.toFixed(2)}`;
}
