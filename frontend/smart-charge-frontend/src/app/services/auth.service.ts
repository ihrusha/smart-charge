import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface RegisterResponse {
  user: { id: string; email: string; created_at: string };
}

export interface Utilisateur {
  id: string;
  email: string;
  created_at: string;
}

export interface MeResponse {
  user: Utilisateur;
}

const CLE_TOKEN = 'smartcharge_token';

@Injectable({ providedIn: 'root' })
export class AuthService {

  private http = inject(HttpClient);
  private apiUrl = 'http://localhost:8000/api/auth';

  private tokenSignal = signal<string | null>(this.lireToken());
  readonly estConnecte = computed(() => this.tokenSignal() !== null);

  get token(): string | null {
    return this.tokenSignal();
  }

  login(email: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.apiUrl}/login`, { email, password })
      .pipe(tap((reponse) => this.enregistrerToken(reponse.accessToken)));
  }

  register(email: string, password: string): Observable<RegisterResponse> {
    return this.http.post<RegisterResponse>(`${this.apiUrl}/register`, { email, password });
  }

  me(): Observable<MeResponse> {
    return this.http.get<MeResponse>(`${this.apiUrl}/me`);
  }

  logout(): void {
    localStorage.removeItem(CLE_TOKEN);
    this.tokenSignal.set(null);
  }

  private enregistrerToken(token: string): void {
    localStorage.setItem(CLE_TOKEN, token);
    this.tokenSignal.set(token);
  }

  private lireToken(): string | null {
    return localStorage.getItem(CLE_TOKEN);
  }
}