import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Borrower } from '../models/borrower.model';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class BorrowerService {
  private baseUrl = `${environment.apiUrl}/api/borrowers`;

  constructor(private http: HttpClient) {}

  getAll(): Observable<Borrower[]> {
    return this.http.get<Borrower[]>(this.baseUrl);
  }

  getById(id: string): Observable<Borrower> {
    return this.http.get<Borrower>(`${this.baseUrl}/${id}`);
  }
}
