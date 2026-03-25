export interface LoanSummary {
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

export interface Payment {
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
