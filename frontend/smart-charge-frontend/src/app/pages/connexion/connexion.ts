import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-connexion',
  imports: [FormsModule, RouterLink],
  templateUrl: './connexion.html',
})
export class Connexion {

  private auth = inject(AuthService);
  private router = inject(Router);

  email = '';
  motDePasse = '';
  erreur = signal('');
  envoi = signal(false);
  motDePasseVisible = signal(false);

  basculerVisibilite(): void {
    this.motDePasseVisible.update((v) => !v);
  }

  seConnecter(): void {
    this.erreur.set('');
    this.envoi.set(true);

    this.auth.login(this.email, this.motDePasse).subscribe({
      next: () => {
        this.envoi.set(false);
        this.router.navigate(['/bornes']);
      },
      error: (err) => {
        this.envoi.set(false);
        this.erreur.set(
          err.status === 401
            ? 'Adresse e-mail ou mot de passe incorrect.'
            : err.error?.error ?? 'Connexion impossible pour le moment.'
        );
      },
    });
  }
}