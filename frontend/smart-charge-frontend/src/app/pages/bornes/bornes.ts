
import {
  Component,
  OnInit,
  AfterViewInit,
  OnDestroy,
  inject,
  signal
} from '@angular/core';

import * as L from 'leaflet';

import { Borne, BorneService } from '../../services/borne.service';

@Component({
  selector: 'app-bornes',
  imports: [],
  templateUrl: './bornes.html',
  styleUrl: './bornes.css'
})
export class Bornes implements OnInit, AfterViewInit, OnDestroy {

  private borneService = inject(BorneService);

  // Données récupérées depuis le BFF
  bornes = signal<Borne[]>([]);
  chargement = signal(true);
  erreur = signal('');
  
  // Filtres sélectionnés
  rechercheVille = signal('');
  filtrePuissance = signal(false);
  filtreConnecteur = signal('');

  // Appliquer les filtres
  appliquerFiltres(): void {
    this.chargement.set(true);
    this.erreur.set('');

    const ville = this.rechercheVille().trim() || undefined;
    const puissanceMin = this.filtrePuissance() ? 50 : undefined;
    const typeConnecteur = this.filtreConnecteur() || undefined;

    this.borneService.getBornes(
      ville,
      puissanceMin,
      typeConnecteur
    ).subscribe({
      next: (data) => {
        this.bornes.set(data);
        this.chargement.set(false);
        this.afficherBornesSurCarte(data);
      },
      error: (err) => {
        console.error('Erreur de filtrage :', err);
        this.erreur.set('Impossible de filtrer les bornes.');
        this.chargement.set(false);
      }
    });
  }

  // Réinitialiser les filtres
  reinitialiserFiltres(): void {
    this.rechercheVille.set('');
    this.filtrePuissance.set(false);
    this.filtreConnecteur.set('');
    this.appliquerFiltres();
  }



  // Carte Leaflet
  private map?: L.Map;
  private marqueurs = L.layerGroup();

  private iconeBorne = L.icon({
    iconUrl: '/leaflet/marker-icon.png',
    iconRetinaUrl: '/leaflet/marker-icon-2x.png',
    shadowUrl: '/leaflet/marker-shadow.png',
    iconSize: [25, 41],
    iconAnchor: [12, 41],
    popupAnchor: [1, -34],
    shadowSize: [41, 41]
  });

  // Récupération des bornes
  ngOnInit(): void {
    this.borneService.getBornes().subscribe({
      next: (data) => {
        this.bornes.set(data);
        this.chargement.set(false);

        if (this.map) {
          this.afficherBornesSurCarte(data);
        }
      },
      error: (err) => {
        console.error('Erreur API Bornes :', err);
        this.erreur.set('Impossible de récupérer les bornes.');
        this.chargement.set(false);
      }
    });
  }

  // Initialisation de la carte
  ngAfterViewInit(): void {
    this.initialiserCarte();

    setTimeout(() => {
      this.map?.invalidateSize();
    }, 300);
  }

  private initialiserCarte(): void {
    this.map = L.map('map').setView(
      [48.8566, 2.3522],
      11
    );

    L.tileLayer(
      'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
      {
        attribution: '&copy; OpenStreetMap contributors',
        maxZoom: 19
      }
    ).addTo(this.map);

    this.marqueurs.addTo(this.map);

    if (this.bornes().length > 0) {
      this.afficherBornesSurCarte(this.bornes());
    }
  }

  // Affichage des bornes sur la carte
  private afficherBornesSurCarte(bornes: Borne[]): void {
    if (!this.map) {
      return;
    }

    this.marqueurs.clearLayers();

    bornes.forEach((borne) => {
      const marqueur = L.marker(
        [borne.latitude, borne.longitude],
        { icon: this.iconeBorne }
      );

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
      marqueur.addTo(this.marqueurs);
    });
  }

  // Nettoyage lorsque l'on quitte la page
  ngOnDestroy(): void {
    this.map?.remove();
  }
}
