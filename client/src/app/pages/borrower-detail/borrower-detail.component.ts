import { Component, OnInit } from '@angular/core';
import { CommonModule, CurrencyPipe, DecimalPipe } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatTableModule } from '@angular/material/table';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Borrower } from '../../models/borrower.model';
import { BorrowerService } from '../../services/borrower.service';

@Component({
  selector: 'app-borrower-detail',
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
    CurrencyPipe,
    DecimalPipe,
  ],
  templateUrl: './borrower-detail.component.html',
  styleUrl: './borrower-detail.component.scss',
})
export class BorrowerDetailComponent implements OnInit {
  borrower: Borrower | null = null;
  loading = true;
  loanColumns = [
    'loanAccountNumber',
    'productDescription',
    'originalAmount',
    'currentBalance',
    'interestRate',
    'status',
    'actions',
  ];

  constructor(
    private route: ActivatedRoute,
    private borrowerService: BorrowerService,
  ) {}

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.borrowerService.getById(id).subscribe({
      next: (borrower) => {
        this.borrower = borrower;
        this.loading = false;
      },
      error: (err) => {
        console.error('Failed to load borrower', err);
        this.loading = false;
      },
    });
  }
}
