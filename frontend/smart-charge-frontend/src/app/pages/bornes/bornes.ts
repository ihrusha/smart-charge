
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

import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-bornes',
  imports: [RouterLink],
  templateUrl: './bornes.html',
  styleUrl: './bornes.css'
})
export class Bornes implements OnInit, AfterViewInit, OnDestroy {

  private borneService = inject(BorneService);

  // Données récupérées depuis le BFF
  bornes = signal<Borne[]>([]);
  chargement = signal(true);
  erreur = signal('');

  
// Liste des identifiants des bornes favorites
favoris = signal<string[]>([]);

// Ajouter ou retirer une borne des favoris
toggleFavori(id: string): void {
  this.favoris.update(liste =>
    liste.includes(id)
      ? liste.filter(favoriId => favoriId !== id)
      : [...liste, id]
  );
}

// Vérifier si une borne est favorite
estFavori(id: string): boolean {
  return this.favoris().includes(id);
}

  
  // Filtres sélectionnés
  rechercheVille = signal('');
  filtrePuissance = signal(false);
  filtreConnecteur = signal('');

    // Simulation : une borne hors service
  private borneHorsServiceId: string | null = null;

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

  private creerIconeBorne(disponible: boolean): L.DivIcon {
    const couleur = disponible ? '#168365' : '#f59e0b';
    
    return L.divIcon({
      className: 'marqueur-borne',
      html: `
       <div style="
        width: 24px;
        height: 24px;
        background: ${couleur};
        border: 3px solid white;
        border-radius: 50%;
        box-shadow: 0 2px 6px rgba(0,0,0,0.25);
       "></div>
      `,
      iconSize: [24, 24],
      iconAnchor: [12, 12],
      popupAnchor: [0, -12]
    });
}


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
    if (this.borneHorsServiceId === null && bornes.length > 0) {
      this.borneHorsServiceId = bornes[0].id;
    }

    bornes.forEach((borne, index) => {
      // Démonstration : une seule borne hors service
      const disponible = borne.id !== this.borneHorsServiceId;
      const marqueur = L.marker(
        [borne.latitude, borne.longitude],
        { icon: this.creerIconeBorne(true) }
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
