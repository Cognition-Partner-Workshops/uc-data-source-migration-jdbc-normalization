import { Component, OnInit } from '@angular/core';
import { CommonModule, CurrencyPipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDividerModule } from '@angular/material/divider';
import { LoanSummary, Payment } from '../../models/loan.model';
import { LoanService } from '../../services/loan.service';

@Component({
  selector: 'app-loan-detail',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatTableModule,
    MatProgressSpinnerModule,
    MatDividerModule,
    CurrencyPipe,
    DecimalPipe,
  ],
  templateUrl: './loan-detail.component.html',
  styleUrl: './loan-detail.component.scss',
})
export class LoanDetailComponent implements OnInit {
  loan: LoanSummary | null = null;
  payments: Payment[] = [];
  loading = true;
  paymentColumns = [
    'paymentDate',
    'totalAmount',
    'principalAmount',
    'interestAmount',
    'escrowAmount',
    'lateFee',
    'type',
    'status',
  ];

  constructor(
    private route: ActivatedRoute,
    private loanService: LoanService,
  ) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.loanService.getById(id).subscribe({
      next: (loan) => {
        this.loan = loan;
        this.loading = false;
      },
      error: (err) => {
        console.error('Failed to load loan', err);
        this.loading = false;
      },
    });
    this.loanService.getPayments(id).subscribe({
      next: (payments) => (this.payments = payments),
    });
  }
}
