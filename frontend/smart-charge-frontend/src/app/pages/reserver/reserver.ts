import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { Borne, BorneService } from '../../services/borne.service';
import { Creneau, ReservationService } from '../../services/reservation.service';

@Component({
  selector: 'app-reserver',
  imports: [RouterLink],
  templateUrl: './reserver.html',
})
export class Reserver implements OnInit {

  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private borneService = inject(BorneService);
  private reservationService = inject(ReservationService);

  borneId = '';
  borne = signal<Borne | null>(null);

  jours = signal<string[]>([]);          // 7 dates au format AAAA-MM-JJ
  jourChoisi = signal('');
  creneaux = signal<Creneau[]>([]);
  creneauChoisi = signal<string | null>(null);

  chargement = signal(true);
  envoi = signal(false);
  erreur = signal('');
  succes = signal('');

  ngOnInit(): void {
    this.borneId = this.route.snapshot.paramMap.get('borneId') ?? '';

    // Les 7 prochains jours, à partir d'aujourd'hui
    const dates: string[] = [];
    for (let i = 0; i < 7; i++) {
      const d = new Date();
      d.setDate(d.getDate() + i);
      dates.push(this.enTexte(d));
    }
    this.jours.set(dates);
    this.jourChoisi.set(dates[0]);

        // Le BFF n'expose pas encore GET /api/bornes/{id} : on cherche dans la liste
    this.borneService.getBornes().subscribe({
      next: (liste) => {
        const trouvee = liste.find((b) => b.id === this.borneId);
        if (trouvee) {
          this.borne.set(trouvee);
        }
      },
      error: () => {},
    });

    this.chargerCreneaux();
  }

  // Date locale au format AAAA-MM-JJ (toISOString donnerait l'heure UTC)
  private enTexte(d: Date): string {
    const mois = String(d.getMonth() + 1).padStart(2, '0');
    const jour = String(d.getDate()).padStart(2, '0');
    return `${d.getFullYear()}-${mois}-${jour}`;
  }

  libelleJour(date: string): string {
    return new Date(`${date}T12:00:00`).toLocaleDateString('fr-FR', {
      weekday: 'short',
      day: 'numeric',
    });
  }

  // "14:00" → "14:00 – 14:30"
  libelleCreneau(heure: string): string {
    const [h, m] = heure.split(':').map(Number);
    const fin = new Date(2000, 0, 1, h, m + 30);
    const finTexte = `${String(fin.getHours()).padStart(2, '0')}:${String(fin.getMinutes()).padStart(2, '0')}`;
    return `${heure} – ${finTexte}`;
  }

  infobulle(c: Creneau): string {
    switch (c.motif) {
      case 'PASSE': return 'Ce créneau est déjà passé';
      case 'BORNE_OCCUPEE': return 'Cette borne est déjà réservée sur ce créneau';
      case 'DEJA_RESERVE': return 'Vous avez déjà une réservation sur ce créneau';
      default: return '';
    }
  }

  choisirJour(date: string): void {
    this.jourChoisi.set(date);
    this.creneauChoisi.set(null);
    this.succes.set('');
    this.chargerCreneaux();
  }

  choisirCreneau(c: Creneau): void {
    if (!c.disponible) {
      return;
    }
    this.creneauChoisi.set(c.heureDebut);
    this.succes.set('');
  }

  private chargerCreneaux(): void {
    this.chargement.set(true);
    this.erreur.set('');

    this.reservationService.disponibilites(this.borneId, this.jourChoisi()).subscribe({
      next: (d) => {
        this.creneaux.set(d.creneaux);
        this.chargement.set(false);
      },
      error: (err) => {
        this.erreur.set(err.error?.error ?? 'Impossible de charger les créneaux.');
        this.chargement.set(false);
      },
    });
  }

  reserver(): void {
    const heure = this.creneauChoisi();
    if (!heure) {
      return;
    }

    this.envoi.set(true);
    this.erreur.set('');
    this.succes.set('');

    this.reservationService.reserver({
      borneId: this.borneId,
      date: this.jourChoisi(),
      heureDebut: `${heure}:00`,
    }).subscribe({
      next: () => {
        this.envoi.set(false);
        this.succes.set('Réservation confirmée.');
        this.creneauChoisi.set(null);
        this.chargerCreneaux();
      },
      error: (err) => {
        this.envoi.set(false);
        this.erreur.set(err.error?.error ?? 'La réservation a échoué.');
        // Le créneau a pu être pris entre-temps : on rafraîchit
        this.chargerCreneaux();
      },
    });
  }

  versMesReservations(): void {
    this.router.navigate(['/mes-reservations']);
  }
}