import { Router, Request, Response } from 'express';
import { getAllLoans, getLoanById, getPaymentsByLoan } from '../services/loanService';

const router = Router();

router.get('/', (_req: Request, res: Response) => {
  const loans = getAllLoans();
  res.json(loans);
});

router.get('/:id', (req: Request<{ id: string }>, res: Response) => {
  const id = req.params.id as string;
  const loan = getLoanById(id);
  if (!loan) {
    res.status(404).json({ error: `Loan not found: ${id}` });
    return;
  }
  res.json(loan);
});

router.get('/:loanId/payments', (req: Request<{ loanId: string }>, res: Response) => {
  const loanId = req.params.loanId as string;
  const payments = getPaymentsByLoan(loanId);
  res.json(payments);
});

export default router;
