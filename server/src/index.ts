import express from 'express';
import cors from 'cors';
import dotenv from 'dotenv';
import { initDatabase } from './database';
import loanRoutes from './routes/loans';
import borrowerRoutes from './routes/borrowers';

dotenv.config();

const app = express();
const PORT = parseInt(process.env.PORT ?? '3000', 10);

app.use(cors());
app.use(express.json());

app.use('/api/loans', loanRoutes);
app.use('/api/borrowers', borrowerRoutes);

// Health check
app.get('/health', (_req, res) => {
  res.json({ status: 'ok' });
});

async function start() {
  await initDatabase();
  app.listen(PORT, () => {
    console.log(`Loan Service (Express) running on http://localhost:${PORT}`);
  });
}

start().catch((err) => {
  console.error('Failed to start server:', err);
  process.exit(1);
});
