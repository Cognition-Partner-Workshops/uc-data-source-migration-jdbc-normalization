/**
 * Legacy translation utilities — ported 1:1 from Java LoanService.
 * These handle the messy conversion from legacy string fields to proper types.
 */

/** Parse legacy amount strings like "285,000" or "1,487.02" into number. */
export function parseLegacyAmount(amount: string | null): number {
  if (!amount || amount.trim() === '') return 0;
  return parseFloat(amount.replace(/,/g, ''));
}

/** Parse legacy decimal string to number. */
export function parseLegacyDecimal(value: string | null): number {
  if (!value || value.trim() === '') return 0;
  return parseFloat(value.trim());
}

/** Parse legacy integer string to number or null. */
export function parseLegacyInteger(value: string | null): number | null {
  if (!value || value.trim() === '') return null;
  return parseInt(value.trim(), 10);
}

/** Expand loan status codes. */
export function expandStatusCode(code: string | null): string {
  if (!code) return 'Unknown';
  const map: Record<string, string> = {
    ACT: 'Active',
    CLO: 'Closed',
    DFT: 'Default',
    FRB: 'Forbearance',
  };
  return map[code] ?? code;
}

/** Expand property type codes. */
export function expandPropertyType(code: string | null): string {
  if (!code) return 'Unknown';
  const map: Record<string, string> = {
    SFR: 'Single Family Residence',
    CND: 'Condominium',
    MFR: 'Multi-Family Residence',
    TWN: 'Townhouse',
  };
  return map[code] ?? code;
}

/** Expand payment type codes. */
export function expandPaymentType(code: string | null): string {
  if (!code) return 'Unknown';
  const map: Record<string, string> = {
    REG: 'Regular',
    EXT: 'Extra',
    PRT: 'Partial',
    PRE: 'Prepayment',
  };
  return map[code] ?? code;
}

/** Expand payment status codes. */
export function expandPaymentStatus(code: string | null): string {
  if (!code) return 'Unknown';
  const map: Record<string, string> = {
    PST: 'Posted',
    REV: 'Reversed',
    NSF: 'Non-Sufficient Funds',
    PND: 'Pending',
  };
  return map[code] ?? code;
}
