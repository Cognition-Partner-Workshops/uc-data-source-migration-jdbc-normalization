import { getDatabase } from '../database';
import { LegacyBorrower, LegacyLoanAccount, LegacyLoanProduct, LegacyPayment } from '../models/legacy';
import { LoanSummaryDto, BorrowerDto, PaymentDto } from '../models/dto';
import {
  parseLegacyAmount,
  parseLegacyDecimal,
  parseLegacyInteger,
  expandStatusCode,
  expandPropertyType,
  expandPaymentType,
  expandPaymentStatus,
} from '../utils/legacyTransformers';

function queryAll<T>(sql: string, params: unknown[] = []): T[] {
  const db = getDatabase();
  const stmt = db.prepare(sql);
  if (params.length > 0) stmt.bind(params);
  const results: T[] = [];
  while (stmt.step()) {
    results.push(stmt.getAsObject() as T);
  }
  stmt.free();
  return results;
}

function queryOne<T>(sql: string, params: unknown[] = []): T | null {
  const db = getDatabase();
  const stmt = db.prepare(sql);
  if (params.length > 0) stmt.bind(params);
  let result: T | null = null;
  if (stmt.step()) {
    result = stmt.getAsObject() as T;
  }
  stmt.free();
  return result;
}

// =========================================================================
// DTO TRANSLATION (ported from Java LoanService)
// =========================================================================

function toLoanSummary(acct: LegacyLoanAccount, product: LegacyLoanProduct | null): LoanSummaryDto {
  return {
    loanAccountNumber: acct.LN_ACCT_NBR,
    borrowerName: `${acct.BORR_FST_NM} ${acct.BORR_LST_NM}`,
    productDescription: product ? (product.PROD_DESC_TXT ?? acct.PROD_CD) : acct.PROD_CD,
    originalAmount: parseLegacyAmount(acct.LN_ORIG_AMT),
    currentBalance: parseLegacyAmount(acct.LN_CURR_BAL),
    interestRate: parseLegacyDecimal(acct.LN_INT_RT),
    monthlyPayment: parseLegacyAmount(acct.LN_PMT_AMT),
    status: expandStatusCode(acct.LN_STAT_CD),
    originationDate: acct.LN_ORIG_DT,
    propertyAddress: `${acct.PROP_ADDR_LN1}, ${acct.PROP_CTY_NM}, ${acct.PROP_ST_CD} ${acct.PROP_ZIP_CD}`,
    propertyType: expandPropertyType(acct.PROP_TYP_CD),
  };
}

function toBorrowerDto(borrower: LegacyBorrower): BorrowerDto {
  const middle = borrower.BORR_MID_INIT ? ` ${borrower.BORR_MID_INIT}.` : '';
  return {
    id: borrower.BORR_ID,
    fullName: `${borrower.BORR_FST_NM}${middle} ${borrower.BORR_LST_NM}`,
    email: borrower.BORR_EMAIL_ADDR,
    phone: borrower.BORR_PH_NBR,
    city: borrower.BORR_CTY_NM,
    state: borrower.BORR_ST_CD,
    creditScore: parseLegacyInteger(borrower.BORR_CRDT_SCR),
    employmentStatus: borrower.BORR_EMP_STAT,
  };
}

function toPaymentDto(pmt: LegacyPayment): PaymentDto {
  return {
    paymentId: pmt.PMT_SEQ_NBR,
    loanAccountNumber: pmt.LN_ACCT_NBR,
    paymentDate: pmt.PMT_DT,
    totalAmount: parseLegacyAmount(pmt.PMT_AMT),
    principalAmount: parseLegacyAmount(pmt.PMT_PRIN_AMT),
    interestAmount: parseLegacyAmount(pmt.PMT_INT_AMT),
    escrowAmount: parseLegacyAmount(pmt.PMT_ESCROW_AMT),
    lateFee: parseLegacyAmount(pmt.PMT_LATE_FEE),
    type: expandPaymentType(pmt.PMT_TYP_CD),
    status: expandPaymentStatus(pmt.PMT_STAT_CD),
  };
}

// =========================================================================
// PUBLIC SERVICE METHODS
// =========================================================================

export function getAllLoans(): LoanSummaryDto[] {
  const products = queryAll<LegacyLoanProduct>('SELECT * FROM CDW_LN_PROD');
  const productMap = new Map(products.map((p) => [p.PROD_CD, p]));

  const accounts = queryAll<LegacyLoanAccount>('SELECT * FROM CDW_LN_ACCT');
  return accounts.map((acct) => toLoanSummary(acct, productMap.get(acct.PROD_CD) ?? null));
}

export function getLoanById(loanAccountNumber: string): LoanSummaryDto | null {
  const acct = queryOne<LegacyLoanAccount>('SELECT * FROM CDW_LN_ACCT WHERE LN_ACCT_NBR = ?', [loanAccountNumber]);
  if (!acct) return null;

  const product = queryOne<LegacyLoanProduct>('SELECT * FROM CDW_LN_PROD WHERE PROD_CD = ?', [acct.PROD_CD]);
  return toLoanSummary(acct, product);
}

export function getAllBorrowers(): BorrowerDto[] {
  const borrowers = queryAll<LegacyBorrower>('SELECT * FROM CDW_BORR_MSTR');
  return borrowers.map(toBorrowerDto);
}

export function getBorrowerById(borrowerId: string): BorrowerDto | null {
  const borrower = queryOne<LegacyBorrower>('SELECT * FROM CDW_BORR_MSTR WHERE BORR_ID = ?', [borrowerId]);
  if (!borrower) return null;

  const dto = toBorrowerDto(borrower);

  // Attach loans for this borrower
  const products = queryAll<LegacyLoanProduct>('SELECT * FROM CDW_LN_PROD');
  const productMap = new Map(products.map((p) => [p.PROD_CD, p]));

  const accounts = queryAll<LegacyLoanAccount>('SELECT * FROM CDW_LN_ACCT WHERE BORR_ID = ?', [borrowerId]);
  dto.loans = accounts.map((acct) => toLoanSummary(acct, productMap.get(acct.PROD_CD) ?? null));

  return dto;
}

export function getPaymentsByLoan(loanAccountNumber: string): PaymentDto[] {
  const payments = queryAll<LegacyPayment>(
    'SELECT * FROM CDW_PMT_HIST WHERE LN_ACCT_NBR = ? ORDER BY PMT_DT DESC',
    [loanAccountNumber]
  );
  return payments.map(toPaymentDto);
}
