import { Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { AuthService, Utilisateur } from '../../services/auth.service';

@Component({
  selector: 'app-compte',
  imports: [],
  templateUrl: './compte.html',
})
export class Compte implements OnInit {

  private auth = inject(AuthService);
  private router = inject(Router);

  utilisateur = signal<Utilisateur | null>(null);
  chargement = signal(true);
  erreur = signal('');

  ngOnInit(): void {
    this.auth.me().subscribe({
      next: (reponse) => {
        this.utilisateur.set(reponse.user);
        this.chargement.set(false);
      },
      error: () => {
        this.erreur.set('Impossible de charger vos informations.');
        this.chargement.set(false);
      },
    });
  }

  dateInscription(iso: string): string {
    return new Date(iso).toLocaleDateString('fr-FR', {
      day: 'numeric',
      month: 'long',
      year: 'numeric',
    });
  }

  seDeconnecter(): void {
    this.auth.logout();
    this.router.navigate(['/connexion']);
  }
}