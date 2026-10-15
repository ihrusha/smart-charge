import { Injectable, inject, signal } from '@angular/core';
import { AuthService } from './auth.service';

@Injectable({ providedIn: 'root' })
export class FavorisService {
  private auth = inject(AuthService);

  /** Clé de stockage, propre à l'utilisateur connecté. */
  private cle: string | null = null;

  /** Identifiants des bornes mises en favori. */
  readonly favoris = signal<string[]>([]);

  /** Passe à vrai quand l'utilisateur est identifié et ses favoris chargés. */
  readonly pret = signal(false);

  /** À appeler au chargement de la page. */
  charger(): void {
    this.auth.me().subscribe({
      next: (reponse: any) => {
        this.cle = `smartcharge_favoris_${reponse.user.id}`;
        try {
          const brut = localStorage.getItem(this.cle);
          const liste = brut ? JSON.parse(brut) : [];
          this.favoris.set(
            Array.isArray(liste) ? liste.filter((v) => typeof v === 'string') : []
          );
        } catch {
          this.favoris.set([]);
        }
        this.pret.set(true);
      },
      error: () => {
        this.cle = null;
        this.favoris.set([]);
        this.pret.set(false);
      },
    });
  }

  estFavori(id: string): boolean {
    return this.favoris().includes(id);
  }

  /** Ajoute ou retire la borne des favoris, et enregistre. */
  basculer(id: string): void {
    if (!this.cle) return;
    this.favoris.update((liste) =>
      liste.includes(id) ? liste.filter((x) => x !== id) : [...liste, id]
    );
    try {
      localStorage.setItem(this.cle, JSON.stringify(this.favoris()));
    } catch {
      // stockage indisponible : le favori reste actif le temps de la session
    }
  }
}