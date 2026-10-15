import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export type StatutBorne = 'DISPONIBLE' | 'HORS_SERVICE';

export interface Borne {
  id: string;
  nom: string;
  adresse: string;
  ville: string;
  latitude: number;
  longitude: number;
  puissance: number;
  typeConnecteur: string;
  statut: StatutBorne;
}

@Injectable({
  providedIn: 'root'
})
export class BorneService {

  private http = inject(HttpClient);

  private apiUrl = 'http://localhost:8000/api/bornes';

  getBornes(
    ville?: string,
    puissanceMin?: number,
    typeConnecteur?: string
  ): Observable<Borne[]> {

    let params = new HttpParams();

    if (ville) {
      params = params.set('ville', ville);
    }

    if (puissanceMin !== undefined) {
      params = params.set('puissance_min', puissanceMin);
    }

    if (typeConnecteur) {
      params = params.set('type_connecteur', typeConnecteur);
    }

    return this.http.get<Borne[]>(this.apiUrl, { params });
  }

  getBorne(id: string): Observable<Borne> {
    return this.http.get<Borne>(`${this.apiUrl}/${id}`);
  }
}