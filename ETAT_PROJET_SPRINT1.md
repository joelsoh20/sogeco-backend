# SOGECO FLEET MANAGER — État du projet à la fin du sprint 1

**85 fichiers Java**, 2 migrations, 4 fichiers de configuration, 10 tables en base.

> **⚠ Snapshot historique.** Les sections 1 à 6 ci-dessous décrivent fidèlement l'état du projet à la fin du **sprint 1** et sont conservées telles quelles à titre de repère. Le projet a considérablement progressé depuis : **380 fichiers Java**, **24 migrations Flyway** (`V1` à `V24`), **46 tables en base**, **23 modules métier** (contre 8 au sprint 1) — soit l'essentiel de la roadmap S1 à S7 du `CDC_Technique_SOGECO_Fleet_Manager_v1.1.md`. Les sections 7 et 8, qui projetaient le sprint 2, ont été mises à jour en conséquence (2026-08-19).

---

# 1. Racine du projet

| Fichier | Rôle |
|---|---|
| `pom.xml` | Dépendances Maven, Spring Boot 4.1, Java 21 |
| `docker-compose.yml` | PostgreSQL 16 (port 5433) et Redis 7 |
| `Dockerfile` | Image de production multi-étapes |
| `.env` | Variables Docker Compose — **jamais versionné** |
| `env.example.txt` | Modèle du `.env` |
| `.gitignore` | Exclut `target/`, `.env`, `.idea/` |
| `README.md` | Démarrage rapide, règles de développement |
| `DEMARRAGE.md` | Installation complète des outils |
| `SETUP-WINDOWS.md` | Spécificités PowerShell et erreurs courantes |

> **Mise à jour 2026-08-19 :** `README.md`, `Dockerfile` et `env.example.txt` n'existent plus à la racine du dépôt actuel — probablement supprimés ou déplacés en cours de développement. `docker-compose.yml`, `.env` et `.gitignore` sont toujours présents. Un fichier `doc.txt` à la racine n'est référencé par aucun document ici.

---

# 2. Base de données

## `V1__security_and_organization.sql` — 10 tables

`cities` · `agencies` · `roles` · `permissions` · `role_permissions` · `users` · `user_roles` · `refresh_tokens` · `audit_logs` · `system_settings`

Conventions appliquées partout : colonnes d'audit (`version`, `created_at`, `updated_at`, `created_by`, `updated_by`), enums en `VARCHAR` avec contrainte `CHECK`, dates en `TIMESTAMPTZ`, désactivation logique par `active` + `deleted_at`.

## `V2__seed_security.sql` — initialisation technique uniquement

- **50 permissions** réparties en 15 modules
- **7 rôles système** : Admin (50 permissions), Gestionnaire (33), Direction (19), Comptable (18), Agent de flotte (17), Superviseur (15), Chauffeur (1)
- **1 compte administrateur** : `admin@sogeco.cm` / `Sogeco@2026`, changement imposé à la première connexion
- **23 paramètres système** : seuils d'alerte, pondérations de notation, rétention GPS, sécurité

Conformément à la décision D12, aucune donnée métier n'est injectée : ni villes, ni sites, ni camions.

---

# 3. Socle technique — `common/`

## `config/` — 6 classes

| Classe | Rôle |
|---|---|
| `SecurityConfig` | Chaîne de filtres, routes publiques, OAuth2 conditionnel |
| `MethodSecurityConfig` | Active `@PreAuthorize` et branche l'évaluateur maison |
| `JpaAuditingConfig` | Remplit `created_by` / `updated_by` depuis le contexte |
| `CorsConfig` | Origines autorisées, pilotées par propriété |
| `AsyncConfig` | Deux pools : général et `telematicsExecutor` (sprint 5) |
| `OpenApiConfig` | Documentation Swagger avec authentification Bearer |

## `entity/` — 2 classes

`BaseEntity` (identifiant, `@Version`, colonnes d'audit, `equals`/`hashCode` résistant aux proxys Hibernate) et `SoftDeletableEntity` qui y ajoute `active` et `deletedAt`.

## `exception/` — 5 classes

`BusinessException` porte un code renvoyant à une règle du cahier fonctionnel (`RG-5.3`). `GlobalExceptionHandler` centralise toutes les erreurs au format `ProblemDetail` (RFC 7807) et traite séparément validation, intégrité, verrouillage optimiste, ressource absente et accès refusé.

## `security/` — 11 classes

| Classe | Rôle |
|---|---|
| `JwtService` | Génération et validation, claims rôles + permissions |
| `JwtProperties` | Configuration validée **au démarrage**, pas à l'usage |
| `JwtAuthenticationFilter` | Extrait le jeton, alimente le contexte |
| `UserPrincipal` | Utilisateur authentifié, autorités cumulées |
| `CustomUserDetailsService` | Chargement avec `@EntityGraph`, sans requêtes N+1 |
| `TotpService` | RFC 6238 implémenté sans dépendance, Base32 inclus |
| `SogecoPermissionEvaluator` | `hasPermission()` dans `@PreAuthorize` |
| `OAuth2LoginSuccessHandler` | Google, sans création automatique de compte |
| `RestAuthenticationEntryPoint` | 401 en ProblemDetail |
| `RestAccessDeniedHandler` | 403 en ProblemDetail |
| `SecurityUtils` | Utilisateur courant, agence, permissions |

## `util/` — 3 classes

`GeoUtils` (Haversine, point-dans-polygone pour le géorepérage, emprise Cameroun, vitesse implicite), `MoneyUtils` (BigDecimal, divisions protégées, variations de période), `CoordinateParser` (couple collé depuis Google Maps).

## `dto/` — 2 classes

`PageResponse<T>` enveloppe toutes les listes paginées. `PeriodFilter` gère la période par défaut des tableaux de bord.

---

# 4. Modules métier

## `modules/auth/` — 12 fichiers

`AuthService` couvre les règles RG-1.1 à RG-1.6 : connexion, verrouillage après 5 échecs, TOTP, changement de mot de passe, activation en deux temps de la double authentification.

`RefreshTokenService` implémente la rotation stricte : à chaque usage l'ancien jeton est révoqué, seule l'empreinte SHA-256 est stockée, et le rejeu d'un jeton révoqué invalide toutes les sessions.

Endpoints : `/login` · `/refresh` · `/logout` · `/me` · `/change-password` · `/2fa/setup` · `/2fa/confirm` · `/2fa/disable`

## `modules/user/` — 8 fichiers

CRUD complet avec garde-fous : impossible de retirer le rôle Administrateur au dernier administrateur, de suspendre son propre compte ou le dernier admin actif. Toute modification de rôles révoque les jetons. Réinitialisation de mot de passe avec valeur temporaire affichée une seule fois.

## `modules/role/` — 8 fichiers

Rôles et permissions en base, extensibles. Les rôles système ne se suppriment pas, les permissions de `ROLE_ADMIN` ne se modifient pas. Le changement de permissions est tracé avec l'ancienne et la nouvelle liste.

## `modules/city/` — 5 fichiers

Référentiel ouvert. `findOrCreate()` crée une ville à la volée lors du géocodage d'une adresse de livraison, avec génération automatique d'un code court.

## `modules/agency/` — 5 fichiers

Sites : siège, agences, dépôts. Saisie des coordonnées par collage Google Maps. Interdiction de désactiver le dernier site actif.

## `modules/audit/` — 6 fichiers

Table en ajout seul, transaction indépendante (`REQUIRES_NEW`) pour qu'un échec métier n'efface pas la trace de la tentative. Une erreur d'écriture d'audit ne remonte jamais à l'appelant.

## `modules/setting/` — 3 fichiers

Accès typé aux paramètres, avec valeur de repli systématique : un seuil corrompu ne doit jamais empêcher le fonctionnement.

---

# 5. Écarts assumés par rapport au cahier technique

| Prévu | Retenu | Motif |
|---|---|---|
| Aspect AOP pour l'audit | Appels explicites dans les services | Un aspect générique ne capture pas *quelle valeur* a changé |
| MapStruct pour les mappers | Méthodes statiques `Response.from(entity)` | Le processeur d'annotations ne s'active pas ; explicite et sans risque |
| jjwt avec Jackson | jjwt avec **Gson** | `jjwt-jackson` cible Jackson 2, Spring Boot 4 embarque Jackson 3 |
| Bibliothèque TOTP | Implémentation RFC 6238 maison | 40 lignes, une dépendance de moins |

---

# 6. Pièges Spring Boot 4 rencontrés

Tous ont la même racine, la **modularisation** de la version 4 : chaque technologie a désormais son module et son package dédiés.

| Symptôme | Correction |
|---|---|
| `flyway-core` déclaré, migrations jamais exécutées | `spring-boot-starter-flyway` |
| `package ...test.autoconfigure.web.servlet does not exist` | `spring-boot-starter-webmvc-test` + package `org.springframework.boot.webmvc.test.autoconfigure` |
| springdoc `NoClassDefFoundError` | springdoc **3.x** (Jackson 3) |
| `No enum constant SerializationFeature.write-dates-as-timestamps` | Propriété supprimée, ISO-8601 par défaut |
| Testcontainers sans version | Import explicite du BOM |
| `UNPROCESSABLE_ENTITY` déprécié | `UNPROCESSABLE_CONTENT` |
| Deux beans `CorsConfigurationSource` | `@Qualifier` |
| `Client id of registration 'google' must not be empty` | Bloc OAuth2 commenté tant qu'il n'y a pas d'identifiants |

---

# 7. Ce qui reste à faire avant le sprint 2 — *mis à jour, statut réel au 2026-08-19*

1. ~~**Commiter.**~~ **Toujours vrai et maintenant critique** : malgré 380 fichiers Java et 24 migrations, **le dépôt Git n'est toujours pas initialisé** (`.git/` absent). Tout ce volume de travail n'a aucune sauvegarde versionnée.
2. **Toujours vrai** : aucun dossier `docs/` n'existe — les cahiers des charges (`CDC_Technique...md`, `Revue_Maquettes...md`, le présent fichier) sont toujours à la racine du dépôt, non versionnés puisque le dépôt Git lui-même n'existe pas.
3. Saisie du référentiel réel (villes, sites) : non vérifiable depuis le code seul — dépend des données chargées en base.
4. Changement du mot de passe administrateur : non vérifiable depuis le code seul.

---

# 8. Sprint 2 — *terminé, et largement dépassé*

Le contenu initialement prévu pour le sprint 2 est fait : les modules `vehicle/`, `driver/`, `document/` existent, avec `vehicle_assignments`, `driver_ratings`, `driver_bonuses`, `driver_actions` en base. Le développement s'est poursuivi bien au-delà — 15 modules métier supplémentaires sont désormais présents :

| Domaine | Modules | Repère CDC |
|---|---|---|
| Parc & RH (S2) | `vehicle`, `driver`, `document` | §18 |
| Commercial & missions (S3) | `client`, `mission`, `route`, `routing` | §18 |
| Coûts (S4) | `partner`, `fuel`, `maintenance`, `expense` | §18 |
| Temps réel (S5) | `tracking`, `alert`, `geofence` | §18 |
| Conformité (S6) | `insurance` | §18 |
| Décisionnel (S7) | `reporting` | §18 |
| Référentiel affiné | `quartier` (sous `city`/`agency`, ajouté en cours de route) | — |

Tables ajoutées en cours de route et absentes du modèle initial du CDC : `tariffs` (grille tarifaire), `geofence_zones` / `vehicle_geofences` / `geofence_events`, `driver_bonuses`, `driver_actions`, `cartes_bleues`, `cartes_grises`, `transport_licenses`, `claims`, `policy_vehicles`, `vehicle_diagnostics`, `routes`, `quartiers`, `generated_reports`, `notifications` — cf. `CDC_Technique_SOGECO_Fleet_Manager_v1.1.md` §6.1 pour le détail des écarts par rapport à la conception initiale.

Règles clés du sprint 2 d'origine (documents à échéance bloquant l'affectation, kilométrage croissant, un seul chauffeur actif par camion) : toujours pertinentes, non re-vérifiées ligne à ligne dans le cadre de cette mise à jour documentaire.
