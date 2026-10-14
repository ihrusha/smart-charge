import { Routes } from '@angular/router';

import { authGuard } from './guards/auth.guard';
import { Bornes } from './pages/bornes/bornes';
import { Connexion } from './pages/connexion/connexion';
import { Inscription } from './pages/inscription/inscription';
import { MesReservations } from './pages/mes-reservations/mes-reservations';
import { Reserver } from './pages/reserver/reserver';

export const routes: Routes = [
  { path: '', redirectTo: 'bornes', pathMatch: 'full' },
  { path: 'connexion', component: Connexion },
  { path: 'inscription', component: Inscription },
  { path: 'bornes', component: Bornes, canActivate: [authGuard] },
  { path: 'mes-reservations', component: MesReservations, canActivate: [authGuard] },
  { path: 'reserver/:borneId', component: Reserver, canActivate: [authGuard] },
  { path: '**', redirectTo: 'bornes' },
];