import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-inscription',
  imports: [FormsModule, RouterLink],
  templateUrl: './inscription.html',
})
export class Inscription {

  private auth = inject(AuthService);
  private router = inject(Router);

  email = '';
  motDePasse = '';
  confirmation = '';
  erreur = signal('');
  envoi = signal(false);

  creerLeCompte(): void {
    this.erreur.set('');

    if (this.motDePasse !== this.confirmation) {
      this.erreur.set('Les deux mots de passe ne correspondent pas.');
      return;
    }
    // Le service auth exige entre 12 et 128 caractères
    if (this.motDePasse.length < 12) {
      this.erreur.set('Le mot de passe doit faire au moins 12 caractères.');
      return;
    }

    this.envoi.set(true);

    this.auth.register(this.email, this.motDePasse).subscribe({
      next: () => {
        // Compte créé : on enchaîne sur la connexion
        this.auth.login(this.email, this.motDePasse).subscribe({
          next: () => {
            this.envoi.set(false);
            this.router.navigate(['/bornes']);
          },
          error: () => {
            this.envoi.set(false);
            this.router.navigate(['/connexion']);
          },
        });
      },
      error: (err) => {
        this.envoi.set(false);
        this.erreur.set(
          err.status === 409
            ? 'Un compte existe déjà avec cette adresse e-mail.'
            : err.error?.error ?? 'Création du compte impossible.'
        );
      },
    });
  }
}