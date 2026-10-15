import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  // Connexion et inscription n'ont pas de token : les autres routes en ont besoin,
  // y compris /api/auth/me
  const estAppelPublic = req.url.includes('/api/auth/login')
    || req.url.includes('/api/auth/register');

  const token = auth.token;

  const requete = token && !estAppelPublic
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(requete).pipe(
    catchError((erreur) => {
      // Token absent, expiré ou invalide : on repart de la connexion
      if (erreur.status === 401 && !estAppelPublic) {
        auth.logout();
        router.navigate(['/connexion']);
      }
      return throwError(() => erreur);
    })
  );
};