import { Component, OnInit } from '@angular/core';
import { CommonModule, CurrencyPipe, DecimalPipe } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { LoanSummary } from '../../models/loan.model';
import { LoanService } from '../../services/loan.service';

@Component({
  selector: 'app-loan-list',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    MatTableModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    CurrencyPipe,
    DecimalPipe,
  ],
  templateUrl: './loan-list.component.html',
  styleUrl: './loan-list.component.scss',
})
export class LoanListComponent implements OnInit {
  loans: LoanSummary[] = [];
  loading = true;
  displayedColumns = [
    'loanAccountNumber',
    'borrowerName',
    'productDescription',
    'originalAmount',
    'currentBalance',
    'interestRate',
    'status',
    'actions',
  ];

  constructor(private loanService: LoanService) {}

  ngOnInit(): void {
    this.loanService.getAll().subscribe({
      next: (data) => {
        this.loans = data;
        this.loading = false;
      },
      error: (err) => {
        console.error('Failed to load loans', err);
        this.loading = false;
      },
    });
  }
}
