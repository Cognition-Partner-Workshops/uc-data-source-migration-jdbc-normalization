import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: 'loans', pathMatch: 'full' },
  {
    path: 'loans',
    loadComponent: () =>
      import('./pages/loan-list/loan-list.component').then((m) => m.LoanListComponent),
  },
  {
    path: 'loans/:id',
    loadComponent: () =>
      import('./pages/loan-detail/loan-detail.component').then((m) => m.LoanDetailComponent),
  },
  {
    path: 'borrowers',
    loadComponent: () =>
      import('./pages/borrower-list/borrower-list.component').then((m) => m.BorrowerListComponent),
  },
  {
    path: 'borrowers/:id',
    loadComponent: () =>
      import('./pages/borrower-detail/borrower-detail.component').then((m) => m.BorrowerDetailComponent),
  },
];
