
import {
  Component,
  OnInit,
  AfterViewInit,
  inject,
  signal
} from '@angular/core';

import { RouterOutlet } from '@angular/router';
import * as L from 'leaflet';

import { Borne, BorneService } from './services/borne.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App implements OnInit, AfterViewInit {

  private borneService = inject(BorneService);

  // Données récupérées depuis l'API
  bornes = signal<Borne[]>([]);
  chargement = signal(true);
  erreur = signal('');

  // Carte Leaflet
  private map!: L.Map;

  // 1. Récupération des bornes depuis le BFF
  ngOnInit(): void {
    this.borneService.getBornes().subscribe({
      next: (data) => {
        this.bornes.set(data);
        this.chargement.set(false);

        if (this.map) {
          this.afficherBornesSurCarte(data);
        }

        console.log('Bornes récupérées :', data);
      },

      error: (err) => {
        console.error('Erreur lors du chargement :', err);
        this.erreur.set('Impossible de récupérer les bornes.');
        this.chargement.set(false);
      }
    });
  }

  // 2. Création de la carte après chargement du HTML
  ngAfterViewInit(): void {
    this.initialiserCarte();
  }

  // 3. Initialisation de la carte OpenStreetMap
  private initialiserCarte(): void {

    // Carte centrée sur Paris
    this.map = L.map('map').setView(
      [48.8566, 2.3522],
      11
    );

    // Fond de carte OpenStreetMap
    L.tileLayer(
      'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
      {
        attribution: '&copy; OpenStreetMap contributors',
        maxZoom: 19
      }
    ).addTo(this.map);

    // Afficher les bornes si elles sont déjà chargées
    if (this.bornes().length > 0) {
      this.afficherBornesSurCarte(this.bornes());
    }
  }

  // 4. Affichage des bornes sur la carte
  private afficherBornesSurCarte(bornes: Borne[]): void {

    if (!this.map) {
      return;
    }

    bornes.forEach((borne) => {

      // Création d'un marqueur
      const iconeBorne = L.icon({
        iconUrl: '/leaflet/marker-icon.png',
        iconRetinaUrl: '/leaflet/marker-icon-2x.png',
        shadowUrl: '/leaflet/marker-shadow.png',
        iconSize: [25, 41],
        iconAnchor: [12, 41],
        popupAnchor: [1, -34],
        shadowSize: [41, 41]});

      const marqueur = L.marker([
        borne.latitude,
        borne.longitude
      ]
      { icon: iconeBorne }
    
    ).addTo(this.map);

      // Informations affichées au clic
      const popup = document.createElement('div');

      const titre = document.createElement('strong');
      titre.textContent = borne.nom;
      popup.appendChild(titre);

      const informations = [
        `Adresse : ${borne.adresse}`,
        `Ville : ${borne.ville}`,
        `Puissance : ${borne.puissance} kW`,
        `Connecteur : ${borne.typeConnecteur}`
      ];

      informations.forEach((information) => {
        const ligne = document.createElement('p');
        ligne.textContent = information;
        popup.appendChild(ligne);
      });

      marqueur.bindPopup(popup);
    });
  }
}
