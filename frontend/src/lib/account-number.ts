export function maskAccountNumber(value: string) {
  const trimmed = value.trim();
  if (trimmed.length <= 4) return trimmed;
  return `${"•".repeat(Math.max(trimmed.length - 4, 4))}${trimmed.slice(-4)}`;
}
