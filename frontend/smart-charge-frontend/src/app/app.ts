
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { Borne, BorneService } from './services/borne.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App implements OnInit {

  private borneService = inject(BorneService);

  bornes = signal<Borne[]>([]);
  chargement = signal(true);
  erreur = signal('');

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
}
