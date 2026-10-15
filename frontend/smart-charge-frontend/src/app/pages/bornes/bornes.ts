
import {
  Component,
  OnInit,
  AfterViewInit,
  OnDestroy,
  inject,
  signal,
  computed
} from '@angular/core';

import * as L from 'leaflet';

import { Borne, BorneService } from '../../services/borne.service';
import { AuthService } from '../../services/auth.service';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-bornes',
  imports: [RouterLink],
  templateUrl: './bornes.html',
  styleUrl: './bornes.css'
})
export class Bornes implements OnInit, AfterViewInit, OnDestroy {

  private borneService = inject(BorneService);
  private authService = inject(AuthService);

  // ===== DONNÉES DES BORNES =====

  bornes = signal<Borne[]>([]);
  chargement = signal(true);
  erreur = signal('');

  // ===== FAVORIS =====

  favoris = signal<string[]>([]);

  // Clé propre à chaque compte utilisateur
  private cleFavoris: string | null = null;

  // Charger les favoris enregistrés dans le navigateur
  chargerFavoris(): void {
    this.authService.me().subscribe({
      next: (reponse) => {
        this.cleFavoris =
          `smartcharge_favoris_${reponse.user.id}`;

        const sauvegarde = localStorage.getItem(
          this.cleFavoris
        );

        try {
          const donnees = sauvegarde
            ? JSON.parse(sauvegarde)
            : [];

          this.favoris.set(
            Array.isArray(donnees)
              ? donnees.filter(
                  (id): id is string => typeof id === 'string'
                )
              : []
          );
        } catch (err) {
          console.error('Favoris enregistrés invalides :', err);
          this.favoris.set([]);
        }
      },

      error: (err) => {
        console.error('Erreur chargement favoris :', err);
        this.cleFavoris = null;
        this.favoris.set([]);
      }
    });
  }

  // Ajouter ou retirer une borne des favoris
  toggleFavori(id: string): void {
    if (!this.cleFavoris) {
      console.warn('Connecte-toi pour enregistrer des favoris.');
      return;
    }

    this.favoris.update(liste =>
      liste.includes(id)
        ? liste.filter(favoriId => favoriId !== id)
        : [...liste, id]
    );

    localStorage.setItem(
      this.cleFavoris,
      JSON.stringify(this.favoris())
    );
  }

  // Vérifier si une borne est favorite
  estFavori(id: string): boolean {
    return this.favoris().includes(id);
  }

  // Afficher les favoris en haut de la liste
  bornesTriees = computed(() => {
    const favoris = this.favoris();

    return [...this.bornes()].sort((a, b) => {
      const aFavori = favoris.includes(a.id);
      const bFavori = favoris.includes(b.id);

      return Number(bFavori) - Number(aFavori);
    });
  });

  // ===== FILTRES =====

  rechercheVille = signal('');
  filtrePuissance = signal(false);
  filtreConnecteur = signal('');

  appliquerFiltres(): void {
    this.chargement.set(true);
    this.erreur.set('');

    const ville = this.rechercheVille().trim() || undefined;
    const puissanceMin = this.filtrePuissance()
      ? 50
      : undefined;
    const typeConnecteur =
      this.filtreConnecteur() || undefined;

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

  reinitialiserFiltres(): void {
    this.rechercheVille.set('');
    this.filtrePuissance.set(false);
    this.filtreConnecteur.set('');
    this.appliquerFiltres();
  }

    // ===== STATUT DES BORNES =====

  // Le statut vient du service Bornes (champ "statut" de l'API)
  estHorsService(id: string): boolean {
    return this.bornes().some((b) => b.id === id && b.statut === 'HORS_SERVICE');
  }

  // ===== CARTE LEAFLET =====

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

  // ===== INITIALISATION =====

  ngOnInit(): void {
    this.chargerFavoris();

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

  // ===== AFFICHAGE DES MARQUEURS =====

  private afficherBornesSurCarte(bornes: Borne[]): void {
    if (!this.map) {
      return;
    }

    this.marqueurs.clearLayers();

    bornes.forEach(borne => {

      const disponible = !this.estHorsService(borne.id);

      const marqueur = L.marker(
        [borne.latitude, borne.longitude],
        {
          icon: this.creerIconeBorne(disponible)
        }
      );

      const popup = document.createElement('div');

      const titre = document.createElement('strong');
      titre.textContent = borne.nom;
      popup.appendChild(titre);

      const informations = [
        `Adresse : ${borne.adresse}`,
        `Ville : ${borne.ville}`,
        `Puissance : ${borne.puissance} kW`,
        `Connecteur : ${borne.typeConnecteur}`,
        `Statut (simulation) : ${
          disponible ? 'Disponible' : 'Hors service'
        }`
      ];

      informations.forEach(information => {
        const ligne = document.createElement('p');
        ligne.textContent = information;
        popup.appendChild(ligne);
      });

      marqueur.bindPopup(popup);
      marqueur.addTo(this.marqueurs);
    });
  }

  // ===== NETTOYAGE =====

  ngOnDestroy(): void {
    this.map?.remove();
  }
}
