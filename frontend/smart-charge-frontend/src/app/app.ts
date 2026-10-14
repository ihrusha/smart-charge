import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AuthService } from './services/auth.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {

  private router = inject(Router);
  protected auth = inject(AuthService);

  seDeconnecter(): void {
    this.auth.logout();
    this.router.navigate(['/connexion']);
  }
}