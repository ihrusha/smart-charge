import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export type Statut = 'CONFIRMEE' | 'ANNULEE';

export interface Reservation {
  id: string;
  borneId: string;
  userId: string;
  date: string;        // AAAA-MM-JJ
  heureDebut: string;  // HH:mm:ss
  statut: Statut;
  creeLe: string;
}

export interface NouvelleReservation {
  borneId: string;
  date: string;
  heureDebut: string;
}

@Injectable({ providedIn: 'root' })
export class ReservationService {

  private http = inject(HttpClient);
  private apiUrl = 'http://localhost:8000/api/reservations';

  mesReservations(): Observable<Reservation[]> {
    return this.http.get<Reservation[]>(this.apiUrl);
  }

  reserver(reservation: NouvelleReservation): Observable<Reservation> {
    return this.http.post<Reservation>(this.apiUrl, reservation);
  }

  annuler(id: string): Observable<Reservation> {
    return this.http.patch<Reservation>(`${this.apiUrl}/${id}/annulation`, {});
  }
}