import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { LoanSummary, Payment } from '../models/loan.model';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class LoanService {
  private baseUrl = `${environment.apiUrl}/api/loans`;

  constructor(private http: HttpClient) {}

  getAll(): Observable<LoanSummary[]> {
    return this.http.get<LoanSummary[]>(this.baseUrl);
  }

  getById(id: string): Observable<LoanSummary> {
    return this.http.get<LoanSummary>(`${this.baseUrl}/${id}`);
  }

  getPayments(loanId: string): Observable<Payment[]> {
    return this.http.get<Payment[]>(`${this.baseUrl}/${loanId}/payments`);
  }
}
