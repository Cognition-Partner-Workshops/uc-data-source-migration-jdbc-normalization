import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Borrower } from '../../models/borrower.model';
import { BorrowerService } from '../../services/borrower.service';

@Component({
  selector: 'app-borrower-list',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    MatTableModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './borrower-list.component.html',
  styleUrl: './borrower-list.component.scss',
})
export class BorrowerListComponent implements OnInit {
  borrowers: Borrower[] = [];
  loading = true;
  displayedColumns = [
    'id',
    'fullName',
    'email',
    'phone',
    'city',
    'state',
    'creditScore',
    'employmentStatus',
    'actions',
  ];

  constructor(private borrowerService: BorrowerService) {}

  ngOnInit(): void {
    this.borrowerService.getAll().subscribe({
      next: (data) => {
        this.borrowers = data;
        this.loading = false;
      },
      error: (err) => {
        console.error('Failed to load borrowers', err);
        this.loading = false;
      },
    });
  }
}
