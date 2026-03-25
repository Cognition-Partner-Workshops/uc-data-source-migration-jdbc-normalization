import { LoanSummary } from './loan.model';

export interface Borrower {
  id: string;
  fullName: string;
  email: string | null;
  phone: string | null;
  city: string | null;
  state: string | null;
  creditScore: number | null;
  employmentStatus: string | null;
  loans?: LoanSummary[];
}
