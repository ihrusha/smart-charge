import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { AuthService } from '../services/auth.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  // On n'ajoute pas le token aux appels de connexion et d'inscription
  const estAppelAuth = req.url.includes('/api/auth/');
  const token = auth.token;

  const requete = token && !estAppelAuth
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(requete).pipe(
    catchError((erreur) => {
      // Token absent, expiré ou invalide : on repart de la connexion
      if (erreur.status === 401 && !estAppelAuth) {
        auth.logout();
        router.navigate(['/connexion']);
      }
      return throwError(() => erreur);
    })
  );
};