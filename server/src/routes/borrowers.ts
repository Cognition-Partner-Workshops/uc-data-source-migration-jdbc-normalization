import { Router, Request, Response } from 'express';
import { getAllBorrowers, getBorrowerById } from '../services/loanService';

const router = Router();

router.get('/', (_req: Request, res: Response) => {
  const borrowers = getAllBorrowers();
  res.json(borrowers);
});

router.get('/:id', (req: Request<{ id: string }>, res: Response) => {
  const id = req.params.id as string;
  const borrower = getBorrowerById(id);
  if (!borrower) {
    res.status(404).json({ error: `Borrower not found: ${id}` });
    return;
  }
  res.json(borrower);
});

export default router;
