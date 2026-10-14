import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { forkJoin } from 'rxjs';

import { Borne, BorneService } from '../../services/borne.service';
import { Reservation, ReservationService } from '../../services/reservation.service';

@Component({
  selector: 'app-mes-reservations',
  imports: [],
  templateUrl: './mes-reservations.html',
})
export class MesReservations implements OnInit {

  private reservationService = inject(ReservationService);
  private borneService = inject(BorneService);

  reservations = signal<Reservation[]>([]);
  private nomsDesBornes = signal<Record<string, string>>({});

  chargement = signal(true);
  erreur = signal('');
  onglet = signal<'avenir' | 'passees'>('avenir');
  annulationEnCours = signal<string | null>(null);

  // Une réservation est "à venir" si son créneau n'a pas encore commencé
  private estAVenir(r: Reservation): boolean {
    return new Date(`${r.date}T${r.heureDebut}`) > new Date();
  }

  reservationsAffichees = computed(() => {
    const avenir = this.onglet() === 'avenir';
    return this.reservations()
      .filter((r) => this.estAVenir(r) === avenir)
      .sort((a, b) =>
        `${a.date}T${a.heureDebut}`.localeCompare(`${b.date}T${b.heureDebut}`)
      );
  });

  ngOnInit(): void {
    forkJoin({
      reservations: this.reservationService.mesReservations(),
      bornes: this.borneService.getBornes(),
    }).subscribe({
      next: ({ reservations, bornes }) => {
        this.reservations.set(reservations);
        this.nomsDesBornes.set(
          bornes.reduce((acc: Record<string, string>, b: Borne) => {
            acc[b.id] = `${b.nom} · ${b.ville}`;
            return acc;
          }, {})
        );
        this.chargement.set(false);
      },
      error: () => {
        this.erreur.set('Impossible de charger vos réservations.');
        this.chargement.set(false);
      },
    });
  }

  nomBorne(borneId: string): string {
    return this.nomsDesBornes()[borneId] ?? 'Borne inconnue';
  }

  // "2026-11-05" et "10:00:00" → "jeudi 5 novembre · 10:00 – 10:30"
  creneau(r: Reservation): string {
    const debut = new Date(`${r.date}T${r.heureDebut}`);
    const fin = new Date(debut.getTime() + 30 * 60 * 1000);

    const jour = debut.toLocaleDateString('fr-FR', {
      weekday: 'long',
      day: 'numeric',
      month: 'long',
    });
    const heure = (d: Date) =>
      d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });

    return `${jour} · ${heure(debut)} – ${heure(fin)}`;
  }

  changerOnglet(valeur: 'avenir' | 'passees'): void {
    this.onglet.set(valeur);
  }

  annuler(r: Reservation): void {
    this.erreur.set('');
    this.annulationEnCours.set(r.id);

    this.reservationService.annuler(r.id).subscribe({
      next: (misAJour) => {
        this.reservations.update((liste) =>
          liste.map((item) => (item.id === misAJour.id ? misAJour : item))
        );
        this.annulationEnCours.set(null);
      },
      error: (err) => {
        this.annulationEnCours.set(null);
        this.erreur.set(err.error?.error ?? "L'annulation a échoué.");
      },
    });
  }
}