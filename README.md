# SmartCharge

Application de réservation de bornes de recharge pour véhicules électriques.
Architecture en microservices avec un BFF (Backend For Frontend) et un frontend Angular.

## Architecture

| Service | Technologie | Port | Base de données |
|---|---|---|---|
| `frontend` | Angular | 4200 | — |
| `bff` | FastAPI (Python) | 8000 | — |
| `auth` | Express (TypeScript) | 3003 | PostgreSQL (5432) |
| `bornes` | Spring Boot (Java 21) | 3001 | MongoDB (27017) |
| `reservations` | Spring Boot (Java 21) | 3002 | MongoDB (27018) |

Le frontend n'appelle jamais les services directement : toutes ses requêtes passent
par le BFF, qui relaie vers le service concerné et transmet l'en-tête `Authorization`.

Le service `reservations` appelle `bornes` pour vérifier qu'une borne existe avant
d'enregistrer une réservation.

## Prérequis

- Docker Desktop
- Node.js 20 ou plus (pour développer le frontend hors conteneur)
- Java 21 (pour développer les services Java hors conteneur)

## Installation

```bash
git clone https://github.com/ihrusha/smart-charge.git
cd smart-charge
cp .env.example .env
```

Renseignez ensuite les valeurs dans `.env` :

| Variable | Rôle |
|---|---|
| `MONGO_BORNES_ROOT_PASSWORD` | Mot de passe root de la base des bornes |
| `MONGO_BORNES_APP_PASSWORD` | Mot de passe applicatif de la base des bornes |
| `MONGO_RESERVATIONS_ROOT_PASSWORD` | Mot de passe root de la base des réservations |
| `MONGO_RESERVATIONS_APP_PASSWORD` | Mot de passe applicatif de la base des réservations |
| `POSTGRES_USERS_PASSWORD` | Mot de passe PostgreSQL du service auth |
| `JWT_SECRET` | Secret de signature des tokens, **32 caractères minimum** |

`JWT_SECRET` doit être identique partout : `auth` signe les tokens et `reservations`
les vérifie. Une valeur différente entraîne des réponses 401 sur toutes les routes
protégées. Pour en générer une :

```bash
openssl rand -base64 48
```

Les mots de passe MongoDB sont insérés dans une URI de connexion : évitez les
caractères `@`, `:`, `/`, `%` et `+`, qui la casseraient.

Le fichier `.env` n'est pas versionné et ne doit jamais être commité.

## Lancement

```bash
docker compose up -d --build
```

Tous les services démarrent, bases comprises. L'application est accessible sur
http://localhost:4200.

Au premier démarrage, le service `bornes` crée automatiquement 30 bornes de test.

Vérifier que tout répond :

```bash
docker compose ps
curl -s http://localhost:8000/health
```

Arrêter :

```bash
docker compose stop        # arrête sans supprimer
docker compose down        # supprime les conteneurs, garde les données
docker compose down -v     # supprime aussi les bases (données perdues)
```

Consulter les logs d'un service :

```bash
docker compose logs -f reservations
```

## Développement

Les conteneurs embarquent une copie du code figée au moment du build : les
modifications locales n'y apparaissent pas tant que l'image n'est pas reconstruite.

### Frontend

Pour bénéficier du rechargement automatique :

```bash
docker compose stop frontend
cd frontend/smart-charge-frontend
npm install
npm start
```

Le frontend doit tourner sur le port **4200** : c'est la seule origine autorisée par
la configuration CORS du BFF.

### Service Java (reservations ou bornes)

```bash
docker compose stop reservations
cd services/reservations
export MONGO_RESERVATIONS_APP_PASSWORD=$(grep '^MONGO_RESERVATIONS_APP_PASSWORD=' ../../.env | cut -d= -f2-)
export JWT_SECRET=$(grep '^JWT_SECRET=' ../../.env | cut -d= -f2-)
./mvnw spring-boot:run
```

Spring Boot ne lit pas le fichier `.env` : les variables doivent être exportées dans
le terminal qui lance l'application, et à chaque nouveau terminal.

### Reconstruire un seul service

```bash
docker compose up -d --build reservations
```

## Tests

Le service `reservations` dispose de 33 tests d'intégration (Testcontainers, un
MongoDB jetable est créé pour l'occasion). Docker doit être démarré.

```bash
cd services/reservations
./mvnw test
```

Les autres services n'ont pas encore de tests automatisés.

## API

Toutes les routes passent par le BFF : `http://localhost:8000`.
Celles marquées 🔒 exigent un en-tête `Authorization: Bearer <token>`.

### Authentification

| Méthode | Route | Description |
|---|---|---|
| POST | `/api/auth/register` | Créer un compte (mot de passe : 12 à 128 caractères) |
| POST | `/api/auth/login` | Se connecter, renvoie un `accessToken` valable 15 minutes |

### Bornes

| Méthode | Route | Description |
|---|---|---|
| GET | `/api/bornes` | Lister les bornes, filtres `ville`, `puissance_min`, `type_connecteur` |

### Réservations 🔒

| Méthode | Route | Description |
|---|---|---|
| GET | `/api/reservations` | Lister ses réservations |
| GET | `/api/reservations/{id}` | Consulter une réservation |
| GET | `/api/reservations/disponibilites?borneId=&date=` | Créneaux d'une borne pour une date |
| POST | `/api/reservations` | Créer une réservation |
| PATCH | `/api/reservations/{id}/annulation` | Annuler une réservation |

Exemple de création :

```json
{
  "borneId": "6acf47ce14a7064ccbcd0c4a",
  "date": "2026-11-20",
  "heureDebut": "14:00:00"
}
```

### Règles métier

- Les créneaux durent 30 minutes et commencent à `:00` ou `:30`, 24h/24.
- Un créneau doit être dans le futur.
- Une borne ne peut avoir qu'une réservation confirmée par créneau.
- Un utilisateur ne peut avoir qu'une réservation confirmée par créneau, toutes bornes confondues.
- Une réservation annulée libère le créneau.
- Un utilisateur ne voit et ne modifie que ses propres réservations.

### Codes de réponse

| Code | Signification |
|---|---|
| 400 | Données invalides (date passée, créneau hors `:00`/`:30`, identifiant mal formé) |
| 401 | Token absent, expiré ou invalide |
| 404 | Ressource introuvable, ou appartenant à un autre utilisateur |
| 409 | Créneau déjà réservé |
| 503 | Service `bornes` indisponible |

Les erreurs ont toutes la forme `{"error": "message"}`.

## Problèmes courants

**Un port est déjà utilisé.** Identifiez le processus puis arrêtez-le :

```bash
lsof -i :4200
kill <PID>
```

**Le frontend n'affiche pas mes modifications.** Le conteneur `frontend` sert une
copie figée : arrêtez-le (`docker compose stop frontend`) et lancez `npm start`.
Après un `git pull`, redémarrez aussi `npm start`.

**Erreur 401 sur toutes les routes.** Le `JWT_SECRET` diffère entre `auth` et
`reservations`, ou le token a expiré (15 minutes).

**Erreur d'authentification MongoDB au démarrage d'un service Java.** Les variables
d'environnement n'ont pas été exportées dans le terminal.

**Cannot find module dans le service auth.** Lancez `npm install` dans
`services/auth` après un `git pull`.

## Organisation du dépôt