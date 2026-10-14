import {
  Component,
  OnInit,
  AfterViewInit,
  inject,
  signal
} from '@angular/core';

import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import * as L from 'leaflet';

import { Borne, BorneService } from './services/borne.service';
import { AuthService } from './services/auth.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App implements OnInit, AfterViewInit {

  private router = inject(Router);
  protected auth = inject(AuthService);
  protected borneService = inject(BorneService)

  bornes = signal<Borne[]>([]);
  chargement = signal(true);
  erreur = signal('');

  private map!: L.Map;

  ngOnInit(): void {
    this.borneService.getBornes().subscribe({
      next: (data) => {
        this.bornes.set(data);
        this.chargement.set(false);
        console.log('Bornes récupérées :', data);
      },
      error: (err) => {
        console.error('Erreur lors du chargement :', err);
        this.erreur.set('Impossible de récupérer les bornes.');
        this.chargement.set(false);
      }
    });
  }

  ngAfterViewInit(): void {
    this.initialiserCarte();
  }

  private initialiserCarte(): void {

    this.map = L.map('map').setView([48.8566, 2.3522], 11);

    L.tileLayer(
      'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
      {
        attribution: '&copy; OpenStreetMap contributors',
        maxZoom: 19
      }
    ).addTo(this.map);

  }

  seDeconnecter(): void {
    this.auth.logout();
    this.router.navigate(['/connexion']);
  }
}
