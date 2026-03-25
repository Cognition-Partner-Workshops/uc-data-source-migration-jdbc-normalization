export interface LoanSummaryDto {
  loanAccountNumber: string;
  borrowerName: string;
  productDescription: string;
  originalAmount: number;
  currentBalance: number;
  interestRate: number;
  monthlyPayment: number;
  status: string;
  originationDate: string | null;
  propertyAddress: string;
  propertyType: string;
}

export interface BorrowerDto {
  id: string;
  fullName: string;
  email: string | null;
  phone: string | null;
  city: string | null;
  state: string | null;
  creditScore: number | null;
  employmentStatus: string | null;
  loans?: LoanSummaryDto[];
}

export interface PaymentDto {
  paymentId: string;
  loanAccountNumber: string;
  paymentDate: string | null;
  totalAmount: number;
  principalAmount: number;
  interestAmount: number;
  escrowAmount: number;
  lateFee: number;
  type: string;
  status: string;
}
