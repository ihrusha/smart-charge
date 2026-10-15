import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { forkJoin } from 'rxjs';

import { Borne, BorneService } from '../../services/borne.service';
import { Reservation, ReservationService } from '../../services/reservation.service';

type Onglet = 'avenir' | 'passees' | 'annulees';

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
  message = signal('');
  onglet = signal<Onglet>('avenir');
  annulationEnCours = signal<string | null>(null);
  suppressionEnCours = signal<string | null>(null);

  // Une réservation est "à venir" si son créneau n'a pas encore commencé
  private estAVenir(r: Reservation): boolean {
    return new Date(`${r.date}T${r.heureDebut}`) > new Date();
  }

  private correspond(r: Reservation, onglet: Onglet): boolean {
    if (onglet === 'annulees') {
      return r.statut === 'ANNULEE';
    }
    if (r.statut === 'ANNULEE') {
      return false;
    }
    return this.estAVenir(r) === (onglet === 'avenir');
  }

  reservationsAffichees = computed(() =>
    this.reservations()
      .filter((r) => this.correspond(r, this.onglet()))
      .sort((a, b) =>
        `${a.date}T${a.heureDebut}`.localeCompare(`${b.date}T${b.heureDebut}`)
      )
  );

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

  messageVide(): string {
    switch (this.onglet()) {
      case 'avenir': return "Vous n'avez aucune réservation à venir.";
      case 'passees': return "Vous n'avez aucune réservation passée.";
      default: return "Vous n'avez annulé aucune réservation.";
    }
  }

  changerOnglet(valeur: Onglet): void {
    this.onglet.set(valeur);
    this.message.set('');
  }

  annuler(r: Reservation): void {
    this.erreur.set('');
    this.message.set('');
    this.annulationEnCours.set(r.id);

    this.reservationService.annuler(r.id).subscribe({
      next: (misAJour) => {
        this.reservations.update((liste) =>
          liste.map((item) => (item.id === misAJour.id ? misAJour : item))
        );
        this.annulationEnCours.set(null);
        this.message.set('Réservation annulée. Vous la retrouvez dans l\'onglet « Annulées ».');
      },
      error: (err) => {
        this.annulationEnCours.set(null);
        this.erreur.set(err.error?.error ?? "L'annulation a échoué.");
      },
    });
  }

  supprimer(r: Reservation): void {
    if (!confirm('Supprimer définitivement cette réservation de votre historique ?')) {
      return;
    }

    this.erreur.set('');
    this.message.set('');
    this.suppressionEnCours.set(r.id);

    this.reservationService.supprimer(r.id).subscribe({
      next: () => {
        this.reservations.update((liste) => liste.filter((item) => item.id !== r.id));
        this.suppressionEnCours.set(null);
        this.message.set('Réservation supprimée.');
      },
      error: (err) => {
        this.suppressionEnCours.set(null);
        this.erreur.set(err.error?.error ?? 'La suppression a échoué.');
      },
    });
  }
}