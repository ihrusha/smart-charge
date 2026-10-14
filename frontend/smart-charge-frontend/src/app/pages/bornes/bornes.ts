import { Component, OnInit, inject, signal } from '@angular/core';

import { Borne, BorneService } from '../../services/borne.service';

@Component({
  selector: 'app-bornes',
  imports: [],
  templateUrl: './bornes.html',
})
export class Bornes implements OnInit {

  private borneService = inject(BorneService);

  bornes = signal<Borne[]>([]);
  chargement = signal(true);
  erreur = signal('');

  ngOnInit(): void {
    this.borneService.getBornes().subscribe({
      next: (data) => {
        this.bornes.set(data);
        this.chargement.set(false);
      },
      error: () => {
        this.erreur.set('Impossible de récupérer les bornes.');
        this.chargement.set(false);
      }
    });
  }
}