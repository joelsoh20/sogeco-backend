# CAHIER DES CHARGES TECHNIQUE — SOGECO FLEET MANAGER
## Version 1.1 — Backend Spring Boot / PostgreSQL

> Document de référence pour le développement. Remplace le cahier technique initial.

---

# 1. Objet et portée

Ce document définit l'architecture technique, la pile logicielle, les patterns de conception et les conventions de développement de la plateforme SOGECO Fleet Manager, couvrant les 12 modules du cahier des charges fonctionnel.

**Périmètre de la version 1 :** l'intégralité des modules fonctionnels, hors facturation client automatisée. Le développement démarre par le backend, sous IntelliJ IDEA, le frontend étant réalisé dans un second temps sur la base des maquettes fournies.

## 1.1 Périmètre physique

| Élément | Valeur |
|---|---|
| Direction | Douala |
| Parc | **11 camions** |
| Agences | **4** : BP Cité, Ndogsimbi, Mboppi, Ndokoti |
| Chauffeurs estimés | 11 à 15 |
| Utilisateurs applicatifs | 5 à 10 (direction + gestionnaires) |
| Zone d'exploitation | Base Douala, missions inter-villes |

Ce dimensionnement est **déterminant pour l'architecture** : à 11 véhicules, le système ne rencontre aucune contrainte de volumétrie. Toutes les décisions techniques ci-après privilégient donc la **simplicité d'exploitation et la rapidité de développement** plutôt que la scalabilité. L'architecture reste néanmoins extensible sans réécriture jusqu'à une centaine de véhicules.

## 1.2 Données de référence à initialiser

Les 4 agences sont créées par migration Flyway dès le sprint 1 (`V2__seed_agencies.sql`), toutes rattachées à Douala. Les coordonnées géographiques sont à relever sur le terrain ou par géocodage, puis corrigées manuellement — elles servent de points d'ancrage sur la carte et de référence pour les départs de mission.

| Code | Nom | Ville |
|---|---|---|
| `DLA-BPC` | BP Cité | Douala |
| `DLA-NDS` | Ndogsimbi | Douala |
| `DLA-MBP` | Mboppi | Douala |
| `DLA-NDK` | Ndokoti | Douala |

> Les 4 agences étant situées dans la même agglomération, l'axe d'analyse « rentabilité par agence » du cahier fonctionnel correspond à une **répartition organisationnelle du parc**, non à une segmentation géographique. Le filtrage par agence doit donc rester disponible sur tous les rapports, mais la carte est centrée sur une seule zone urbaine.

---

# 2. Décisions d'architecture arrêtées

| # | Sujet | Décision | Conséquence technique |
|---|---|---|---|
| D1 | Chiffre d'affaires | Saisi manuellement sur la mission | Champ `revenue_amount` sur `missions`. Pas de tables `invoices` / `invoice_lines` en v1. |
| D2 | Rôles | ~~Figés en enum Java~~ **Révisée : rôles et permissions en base**, entièrement paramétrables (option B identifiée dans *Revue des maquettes*, §11) | Tables `roles` / `permissions` / `role_permissions` / `user_roles` implémentées dès le sprint 1 (module `modules/role/`). Sécurité par `@PreAuthorize` + `SogecoPermissionEvaluator.hasPermission()`, plus qu'un simple enum. |
| D3 | Cartographie | OpenStreetMap, migration Google Maps possible | Abstraction `MapProvider` + `GeocodingService` côté backend, Leaflet côté frontend. |
| D4 | Ingestion GPS | Webhooks entrants | Endpoint public sécurisé par signature HMAC, traitement asynchrone. |
| D5 | Comptes chauffeurs | Aucun | `drivers` sans lien `user_id`. Toutes les saisies terrain passent par un gestionnaire. |
| D6 | Rétention GPS | 90 jours | Partitionnement mensuel + purge planifiée + table d'agrégats journaliers. |
| D7 | Base de données | PostgreSQL autogéré (Docker) | Abandon de la piste Supabase. |
| D8 | Architecture | Monolithe modulaire en couches | Abandon de l'hexagonal strict, trop coûteux pour un développement solo. |
| D9 | Temps réel | WebSocket STOMP over SockJS | Abandon de Socket.io, incompatible avec Spring. |
| D10 | Boîtiers télématiques | Fournisseur communiqué au sprint 5 | Développement sur simulateur de trames + adaptateur par prestataire. Aucune dépendance bloquante avant le sprint 5. |
| D11 | Dimensionnement | 11 camions, 4 agences, mono-instance | Instance unique, broker WebSocket en mémoire, pas de haute disponibilité. |

---

# 3. Pile technique

## 3.1 Backend

| Composant | Version | Justification |
|---|---|---|
| Java | **26** | Choix assumé au-delà de la LTS 21/25 pour suivre la dernière version. **Non-LTS** : support jusqu'à la sortie de Java 27 (~6 mois) seulement — prévoir une bascule vers la prochaine LTS (25 ou 29) à terme. |
| Spring Boot | **4.1.x** | Spring Boot 3.5 a atteint sa fin de support open source le 30 juin 2026 ; la ligne 4.x est la seule encore maintenue. Requiert Spring Framework 7. |
| Spring Web MVC | inclus | API REST. Modèle synchrone, suffisant à cette volumétrie et plus simple à déboguer que WebFlux. |
| Spring Data JPA / Hibernate | inclus | ORM, repositories, Specifications. |
| Spring Security | inclus | Authentification JWT, autorisation par rôle. |
| Spring WebSocket + STOMP | inclus | Diffusion temps réel positions et alertes. |
| Spring Validation | inclus | Validation déclarative des DTO. |
| Spring Scheduling | inclus | Tâches planifiées (échéances, purges, recalculs). |
| Spring Actuator | inclus | Supervision, health checks. |
| PostgreSQL | **16** ou 17 | Base relationnelle principale. |
| Redis | **7.x** | Cache dernières positions, cache KPI, idempotence webhooks, rate limiting. |
| Flyway | dernière | Migrations SQL versionnées. |
| MapStruct | 1.6+ | Génération des mappers Entity ↔ DTO à la compilation. |
| Lombok | dernière | Réduction du code répétitif. |
| jjwt (io.jsonwebtoken) | 0.12+ | Génération et validation des JWT. |
| springdoc-openapi | 2.x | Documentation Swagger auto-générée. |
| Maven | 3.9+ | Gestion des dépendances. |

> **Note de compatibilité :** Spring Boot 4 embarque Spring Security 7 et Hibernate 7. La configuration de sécurité doit impérativement utiliser le DSL lambda ; l'ancienne syntaxe chaînée n'existe plus.

## 3.2 Tests et qualité

| Composant | Usage |
|---|---|
| JUnit 5 | Socle de tests |
| Mockito | Doublures en tests unitaires |
| Testcontainers | PostgreSQL et Redis réels en tests d'intégration |
| Spring Boot Test / MockMvc | Tests de contrôleurs |
| JaCoCo | Couverture, seuil minimal 70 % sur la couche service |
| Spotless + Checkstyle | Formatage et style imposés |

## 3.3 Infrastructure

| Composant | Usage |
|---|---|
| Docker / Docker Compose | PostgreSQL, Redis, backend, frontend |
| Nginx | Reverse proxy, terminaison TLS en production |
| GitHub Actions (ou GitLab CI) | Build, tests, image Docker |
| Logback + JSON | Journalisation structurée |

## 3.4 Frontend (rappel, développé en phase 2)

React 18 + TypeScript + Vite · TailwindCSS + shadcn/ui · TanStack Query (état serveur) · Zustand (état UI) · React Router · React Hook Form + Zod · Recharts (graphiques) · **Leaflet + react-leaflet** (cartographie OSM) · **@stomp/stompjs + sockjs-client** (temps réel) · Axios avec intercepteurs JWT.

---

# 4. Architecture applicative

## 4.1 Principe

**Monolithe modulaire en couches.** Un seul déployable, découpé en modules métier autonomes. Chaque module possède ses propres entités, repositories, services, contrôleurs et DTO.

```
┌──────────────────────────────────────────────┐
│  Controller  — HTTP, validation, sécurité    │
├──────────────────────────────────────────────┤
│  Service     — règles métier, transactions   │
├──────────────────────────────────────────────┤
│  Repository  — accès données (Spring Data)   │
├──────────────────────────────────────────────┤
│  Entity      — mapping JPA                   │
└──────────────────────────────────────────────┘
```

## 4.2 Règles de dépendance (non négociables)

1. Un **contrôleur** n'appelle jamais un repository directement.
2. Un contrôleur n'expose et ne reçoit **que des DTO**, jamais une entité JPA.
3. Un **service** d'un module A appelle le **service** d'un module B, jamais son repository.
4. La transaction est ouverte **au niveau service** (`@Transactional`), jamais au contrôleur.
5. Les lectures sont annotées `@Transactional(readOnly = true)`.
6. Aucune logique métier dans une entité JPA ni dans un contrôleur.

## 4.3 Arborescence des packages

```
src/main/java/com/sogeco/fleet/
│
├── SogecoFleetApplication.java
│
├── common/
│   ├── config/          SecurityConfig, WebSocketConfig, RedisConfig,
│   │                    OpenApiConfig, JacksonConfig, AsyncConfig,
│   │                    CorsConfig, SchedulingConfig
│   ├── security/        JwtService, JwtAuthenticationFilter,
│   │                    UserDetailsServiceImpl, WebhookSignatureFilter,
│   │                    SecurityUtils
│   ├── exception/       GlobalExceptionHandler, BusinessException,
│   │                    ResourceNotFoundException, DuplicateResourceException
│   ├── dto/             PageResponse<T>, ApiError, KpiCard, PeriodFilter
│   ├── entity/          BaseEntity, AuditableEntity
│   ├── enums/           Role, VehicleStatus, MissionStatus, AlertType,
│   │                    AlertSeverity, FuelType, ...
│   ├── util/            DateUtils, GeoUtils (Haversine), MoneyUtils
│   └── event/           DomainEvent, événements applicatifs
│
├── modules/
│   ├── auth/            AuthController, AuthService, LoginRequest,
│   │                    AuthResponse, RefreshTokenService
│   ├── user/            User, UserRepository, UserService, UserController
│   ├── agency/          Agency, AgencyRepository, AgencyService, ...
│   ├── driver/          Driver, DriverRating, DriverService,
│   │                    DriverPerformanceService, DriverController
│   ├── vehicle/         Vehicle, VehicleAssignment, VehicleService,
│   │                    VehicleAssignmentService, VehicleController
│   ├── client/          Client, ServiceType, ClientService, ...
│   ├── mission/         Mission, MissionWaypoint, MissionService,
│   │                    MissionProgressService, MissionController
│   ├── fuel/            FuelLog, FuelService, FuelAnalyticsService,
│   │                    ConsumptionCalculator, FuelController
│   ├── maintenance/     MaintenanceLog, MaintenanceItem,
│   │                    MaintenanceService, MaintenanceController
│   ├── insurance/       InsuranceContract, TechnicalInspection,
│   │                    InsuranceClaim, ComplianceService, ...
│   ├── partner/         Partner, PartnerService, PartnerController
│   ├── expense/         Expense, ExpenseService, ExpenseController
│   ├── alert/           Alert, AlertRule, AlertService, AlertEngine,
│   │                    AlertPublisher, AlertController
│   ├── tracking/        GpsPosition, GpsWebhookController,
│   │                    GpsIngestionService, PositionCacheService,
│   │                    GpsRetentionScheduler, TrackingController
│   ├── geo/             MapProvider (interface), OsmGeocodingAdapter,
│   │                    GoogleGeocodingAdapter, RoutingService
│   ├── dashboard/       DashboardFacade, AdminDashboardService,
│   │                    ExecutiveDashboardService, DashboardController
│   ├── report/          ReportService, FinancialReportService,
│   │                    ExportService (CSV/Excel/PDF), ReportController
│   ├── document/        Document, DocumentService, FileStorageService
│   ├── setting/         SystemSetting, Integration, SettingService
│   └── audit/           AuditLog, AuditService, AuditAspect
│
└── resources/
    ├── application.yml
    ├── application-dev.yml
    ├── application-prod.yml
    └── db/migration/    V1__... à V8__...
```

---

# 5. Patterns de conception à appliquer

## 5.1 Patterns structurels

| Pattern | Où l'appliquer | Bénéfice |
|---|---|---|
| **Repository** | Tous les modules, via Spring Data JPA | Isolation de l'accès aux données |
| **Service Layer** | Tous les modules | Centralisation des règles métier et des transactions |
| **DTO + Mapper** | Toutes les frontières HTTP ; ~~via MapStruct~~ **en pratique via des méthodes statiques `Response.from(entity)`** (le processeur d'annotations MapStruct ne s'est jamais activé correctement — écart documenté dès le sprint 1, voir `ETAT_PROJET_SPRINT1.md` §5) | Aucune fuite d'entité JPA, pas de `LazyInitializationException` en sérialisation |
| **Facade** | `DashboardFacade` | Les dashboards agrègent 8 services ; la façade évite un contrôleur obèse |
| **Adapter** | `MapProvider`, `GeocodingService` | Bascule OSM → Google Maps sans toucher au métier (décision D3) |
| **Builder** | Construction des entités et DTO complexes (via Lombok `@Builder`) | Lisibilité, immuabilité |

## 5.2 Patterns comportementaux

| Pattern | Où l'appliquer | Bénéfice |
|---|---|---|
| **Strategy** | `ConsumptionCalculator`, moteur de règles d'alerte, calcul de coût de mission | Ajouter une règle sans modifier l'existant |
| **Observer / Événements applicatifs** | `ApplicationEventPublisher` : `MissionCompletedEvent`, `AlertTriggeredEvent`, `FuelLogCreatedEvent` | Découple la création d'une alerte de sa diffusion WebSocket et de sa notification |
| **Chain of Responsibility** | Chaîne de filtres Spring Security, chaîne de validation des trames GPS | Traitements en pipeline |
| **Template Method** | `AuditableEntity`, classes de base des services d'export | Factorisation du comportement commun |
| **State** | Cycle de vie mission (`PLANIFIE → EN_COURS → TERMINE / ANNULE`) et alerte (`ACTIVE → PRISE_EN_COMPTE → RESOLUE`) | Transitions illégales bloquées au niveau service |

## 5.3 Patterns d'accès aux données et de performance

| Pattern | Où l'appliquer |
|---|---|
| **Specification (JPA Criteria)** | Filtres combinables sur véhicules, missions, alertes, logs — évite 15 méthodes `findByXAndYAndZ` |
| **Projection (interface ou DTO)** | Listes et KPI : ne charger que les colonnes nécessaires |
| **Cache-aside (Redis)** | Dernière position de chaque véhicule, KPI de dashboard (TTL 60 s), référentiels stables |
| **Write-behind** | Positions GPS : écriture immédiate en Redis, persistance PostgreSQL par lots |
| **Idempotency key** | Webhooks GPS : clé `deviceId + timestamp` en Redis (TTL 24 h) contre les rejeux |
| **Pagination systématique** | Tous les endpoints de liste, `Pageable` obligatoire |
| **Optimistic locking** | `@Version` sur `missions`, `vehicles`, `alerts` — évite l'écrasement concurrent |
| **Soft delete** | `deleted_at` / `active` — aucune suppression physique sur les entités historisées |

## 5.4 Anti-patterns à proscrire

- Entités JPA renvoyées directement par les contrôleurs.
- `FetchType.EAGER` sur les relations (**tout en `LAZY`**, `JOIN FETCH` ou `@EntityGraph` à la demande).
- Requêtes N+1 non détectées : activer `spring.jpa.properties.hibernate.generate_statistics` en développement.
- `ddl-auto: update` — interdit, y compris en développement. Flyway seul fait foi.
- `Double` pour les montants — `BigDecimal` exclusivement.
- `@Enumerated(EnumType.ORDINAL)` — `STRING` exclusivement.
- Logique métier dans les contrôleurs.
- `System.out.println` — Slf4j exclusivement.

---

# 6. Modèle de données v1

## 6.1 Ajustements liés aux décisions

**Tables retirées** (par rapport à l'analyse initiale) : `invoices`, `invoice_lines` (décision D1, la facturation client automatisée reste hors périmètre malgré le retour à un modèle facturé — voir *Revue des maquettes* §7). Le champ `drivers.user_id` disparaît (décision D5).

**Tables ajoutées** : `gps_daily_stats` (agrégats de conservation au-delà de 90 jours), `webhook_events` (journal brut des trames reçues), `roles` / `permissions` / `role_permissions` / `user_roles` (décision D2 révisée, ci-dessus).

> **État réel (24 migrations appliquées, `V1` à `V24`) : 46 tables**, bien au-delà des 27 listées en §6.2 ci-dessous qui reflètent la conception initiale. Les ajouts principaux, tous issus des décisions prises en cours de développement et de la *Revue des maquettes* : `tariffs` (grille tarifaire, §7.2), `geofence_zones` / `vehicle_geofences` / `geofence_events` (géorepérage, §9.1), `driver_bonuses` (§9.2), `driver_actions` (§9.3), `cartes_bleues`, `cartes_grises`, `transport_licenses`, `claims`, `policy_vehicles` (documents et conformité détaillés), `quartiers` (affinement du référentiel géographique sous `cities`/`agencies`), `routes`, `vehicle_diagnostics` (lecture CAN/OBD, §8), `generated_reports`, `notifications`. Se référer à `src/main/resources/db/migration/` pour le détail à jour plutôt qu'à la liste figée ci-dessous.

## 6.2 Liste des tables — 27 tables (conception initiale, dépassée — voir note ci-dessus)

| # | Table | Domaine | Sprint |
|---|---|---|---|
| 1 | `agencies` | Organisation | 1 |
| 2 | `users` | Sécurité | 1 |
| 3 | `refresh_tokens` | Sécurité | 1 |
| 4 | `drivers` | RH | 2 |
| 5 | `driver_ratings` | RH | 2 |
| 6 | `vehicles` | Parc | 2 |
| 7 | `vehicle_assignments` | Parc | 2 |
| 8 | `documents` | Parc | 2 |
| 9 | `clients` | Exploitation | 3 |
| 10 | `service_types` | Exploitation | 3 |
| 11 | `missions` | Exploitation | 3 |
| 12 | `mission_waypoints` | Exploitation | 3 |
| 13 | `partners` | Coûts | 4 |
| 14 | `fuel_logs` | Coûts | 4 |
| 15 | `maintenance_logs` | Coûts | 4 |
| 16 | `maintenance_items` | Coûts | 4 |
| 17 | `expenses` | Coûts | 4 |
| 18 | `insurance_contracts` | Conformité | 6 |
| 19 | `technical_inspections` | Conformité | 6 |
| 20 | `insurance_claims` | Conformité | 6 |
| 21 | `gps_positions` | Temps réel | 5 |
| 22 | `gps_daily_stats` | Temps réel | 5 |
| 23 | `webhook_events` | Temps réel | 5 |
| 24 | `alert_rules` | Temps réel | 5 |
| 25 | `alerts` | Temps réel | 5 |
| 26 | `notifications` | Temps réel | 5 |
| 27 | `system_settings`, `integrations`, `audit_logs` | Système | 1 / 7 |

> Le détail des colonnes figure dans le document *Analyse et modèle de données*. Les compléments ci-dessous s'appliquent.

## 6.3 Compléments de schéma

**`missions` — champs financiers (décision D1)**
```sql
revenue_amount   NUMERIC(15,2) NOT NULL DEFAULT 0,   -- CA saisi manuellement
revenue_notes    VARCHAR(255),                       -- justificatif de saisie
fuel_cost        NUMERIC(15,2) DEFAULT 0,            -- calculé
other_cost       NUMERIC(15,2) DEFAULT 0,            -- saisi
total_cost       NUMERIC(15,2) GENERATED ALWAYS AS (fuel_cost + other_cost) STORED,
margin_amount    NUMERIC(15,2) GENERATED ALWAYS AS (revenue_amount - fuel_cost - other_cost) STORED
```

**`users` — rôle en enum (décision D2)**
```sql
role VARCHAR(20) NOT NULL CHECK (role IN ('ROLE_ADMIN','ROLE_PDG','ROLE_MANAGER'))
```

**`refresh_tokens`**
`id`, `user_id` FK, `token_hash`, `expires_at`, `revoked_at`, `created_ip`

**`webhook_events`** — traçabilité brute de l'ingestion GPS
`id`, `provider`, `device_id`, `payload JSONB`, `signature_valid BOOLEAN`, `received_at`, `processed_at`, `status` (`RECU`, `TRAITE`, `REJETE`, `DOUBLON`), `error_message`

**`gps_daily_stats`** — agrégat conservé au-delà de 90 jours
`id`, `vehicle_id` FK, `stat_date DATE`, `distance_km`, `max_speed`, `avg_speed`, `driving_time_minutes`, `idle_time_minutes`, `first_position_at`, `last_position_at`
> Contrainte d'unicité `(vehicle_id, stat_date)`.

## 6.4 Conventions de persistance

1. Migrations Flyway `V{n}__{description}.sql`, jamais modifiées après application.
2. `spring.jpa.hibernate.ddl-auto: validate` sur tous les profils.
3. `BaseEntity` : `id`, `created_at`, `updated_at`, `created_by`, `updated_by`, `version`, via `@MappedSuperclass` + `@EntityListeners(AuditingEntityListener.class)`.
4. Toutes les relations en `FetchType.LAZY`.
5. Index à créer systématiquement : toutes les clés étrangères, `(vehicle_id, recorded_at DESC)` sur `gps_positions`, `(status, severity, triggered_at DESC)` sur `alerts`, `(status, planned_start_date)` sur `missions`, `(vehicle_id, fuel_date DESC)` sur `fuel_logs`, `end_date` sur `insurance_contracts` et `expiry_date` sur `technical_inspections`.
6. Contrainte d'unicité partielle sur l'affectation courante :
```sql
CREATE UNIQUE INDEX ux_active_assignment
  ON vehicle_assignments (vehicle_id) WHERE end_date IS NULL;
```

---

# 7. Sécurité

## 7.1 Authentification

- **JWT** signé HS256 (secret ≥ 256 bits, hors code source, injecté par variable d'environnement).
- **Access token** : 15 minutes, transporté par l'en-tête `Authorization: Bearer`.
- **Refresh token** : 7 jours, stocké haché en base, rotation à chaque usage, révocable.
- Mots de passe hachés en **BCrypt** (force 12).
- Verrouillage du compte après 5 échecs consécutifs pendant 15 minutes (compteur Redis).

## 7.2 Autorisation

Enum `Role` figé (décision D2) :

| Rôle | Portée |
|---|---|
| `ROLE_ADMIN` | Accès total, administration, utilisateurs, paramètres |
| `ROLE_MANAGER` | Exploitation : parc, chauffeurs, missions, carburant, maintenance, alertes. **Aucun accès aux données financières consolidées.** |
| `ROLE_PDG` | Lecture seule sur l'ensemble + accès exclusif aux dashboards et rapports financiers |

Contrôle par `@PreAuthorize("hasRole('PDG')")` au niveau **service**, pas seulement contrôleur. Le filtrage par agence est appliqué systématiquement pour les rôles non-admin.

## 7.3 Sécurisation des webhooks GPS

L'endpoint `POST /api/v1/webhooks/gps/{provider}` est le seul point d'entrée non authentifié par JWT. Il est protégé par :

1. **Signature HMAC-SHA256** du corps de la requête dans l'en-tête `X-Signature`, secret partagé stocké chiffré dans `integrations.api_key_encrypted`. Comparaison à temps constant.
2. **Horodatage** dans l'en-tête `X-Timestamp`, rejet au-delà de 5 minutes de dérive (anti-rejeu).
3. **Clé d'idempotence** `deviceId:timestamp` en Redis, TTL 24 h.
4. **Rate limiting** par IP (Bucket4j ou compteur Redis).
5. Liste blanche d'adresses IP du prestataire si celui-ci en publie une.
6. Corps limité à 256 Ko.

## 7.4 Autres mesures

- HTTPS obligatoire en production (Let's Encrypt via Nginx).
- CORS restreint aux origines déclarées, par profil.
- Toute entrée validée par Bean Validation ; requêtes JPQL paramétrées uniquement.
- En-têtes de sécurité : `X-Content-Type-Options`, `X-Frame-Options`, `Strict-Transport-Security`.
- Secrets par variables d'environnement / fichier `.env` non versionné.
- Journalisation des actions sensibles dans `audit_logs` via un aspect AOP : authentification, création et suspension de comptes, arrêt d'urgence, modification de montants, suppression logique.
- **Données personnelles** : la géolocalisation des chauffeurs est une donnée personnelle. La rétention à 90 jours (décision D6) doit être documentée et portée à la connaissance des salariés.

---

# 8. API REST

## 8.1 Conventions

- Préfixe versionné : `/api/v1/...`
- Ressources au pluriel, en anglais, en kebab-case : `/api/v1/fuel-logs`
- Verbes HTTP : `GET` (lire), `POST` (créer), `PUT` (remplacer), `PATCH` (modifier partiellement), `DELETE` (désactiver logiquement)
- Codes : `200`, `201` + en-tête `Location`, `204`, `400`, `401`, `403`, `404`, `409` (conflit métier), `422` (validation), `429`, `500`

## 8.2 Pagination et filtrage

```
GET /api/v1/missions?page=0&size=20&sort=plannedStartDate,desc
                    &status=EN_COURS&vehicleId=12&from=2026-01-01&to=2026-03-31
```
Réponse enveloppée dans `PageResponse<T>` : `content`, `page`, `size`, `totalElements`, `totalPages`, `last`. Taille maximale de page : 100.

## 8.3 Format d'erreur

`ProblemDetail` (RFC 7807), produit par un `@RestControllerAdvice` unique :

```json
{
  "type": "https://api.sogeco.cm/errors/validation",
  "title": "Erreur de validation",
  "status": 422,
  "detail": "La quantité doit être strictement positive",
  "instance": "/api/v1/fuel-logs",
  "timestamp": "2026-08-02T10:15:30Z",
  "errors": [{ "field": "quantityLiters", "message": "doit être supérieur à 0" }]
}
```

## 8.4 Endpoints principaux

**Authentification**
```
POST   /api/v1/auth/login
POST   /api/v1/auth/refresh
POST   /api/v1/auth/logout
GET    /api/v1/auth/me
POST   /api/v1/auth/change-password
```

**Parc**
```
GET    /api/v1/vehicles                    (filtres + pagination)
POST   /api/v1/vehicles
GET    /api/v1/vehicles/{id}
PUT    /api/v1/vehicles/{id}
PATCH  /api/v1/vehicles/{id}/status
DELETE /api/v1/vehicles/{id}               (désactivation logique)
GET    /api/v1/vehicles/{id}/registration-card
GET    /api/v1/vehicles/{id}/fuel-history
GET    /api/v1/vehicles/{id}/maintenance-history
GET    /api/v1/vehicles/stats
POST   /api/v1/vehicles/{id}/assignments   (affecter un chauffeur)
DELETE /api/v1/vehicles/{id}/assignments/current
```

**Chauffeurs**
```
GET    /api/v1/drivers
POST   /api/v1/drivers
GET    /api/v1/drivers/{id}
PUT    /api/v1/drivers/{id}
GET    /api/v1/drivers/{id}/performance
GET    /api/v1/drivers/{id}/ratings
POST   /api/v1/drivers/{id}/ratings
GET    /api/v1/drivers/top-performers?criteria=REVENUE|FUEL_SAVING&limit=5
GET    /api/v1/drivers/stats
```

**Missions**
```
GET    /api/v1/missions
POST   /api/v1/missions
GET    /api/v1/missions/{id}
PUT    /api/v1/missions/{id}
PATCH  /api/v1/missions/{id}/progress
POST   /api/v1/missions/{id}/start
POST   /api/v1/missions/{id}/complete      (saisie du CA réalisé)
POST   /api/v1/missions/{id}/cancel
GET    /api/v1/missions/{id}/route
GET    /api/v1/missions/stats
```

**Coûts**
```
GET|POST /api/v1/fuel-logs
GET      /api/v1/fuel-logs/analytics?vehicleId=&from=&to=
GET|POST /api/v1/maintenance-logs
GET      /api/v1/maintenance-logs/{id}
GET      /api/v1/maintenance-logs/analytics
GET|POST /api/v1/expenses
GET|POST /api/v1/partners
```

**Conformité**
```
GET|POST /api/v1/insurance-contracts
GET      /api/v1/insurance-contracts/expiring?days=30
GET|POST /api/v1/technical-inspections
GET|POST /api/v1/insurance-claims
GET      /api/v1/compliance/dashboard
```

**Alertes**
```
GET    /api/v1/alerts?status=&severity=&from=&to=
GET    /api/v1/alerts/{id}
POST   /api/v1/alerts/{id}/acknowledge
POST   /api/v1/alerts/{id}/resolve
GET    /api/v1/alerts/stats
GET|PUT /api/v1/alert-rules
```

**Suivi GPS**
```
GET    /api/v1/tracking/positions/current            (toutes les dernières positions, depuis Redis)
GET    /api/v1/tracking/vehicles/{id}/position
GET    /api/v1/tracking/vehicles/{id}/history?from=&to=
GET    /api/v1/tracking/vehicles/{id}/daily-stats?from=&to=
POST   /api/v1/webhooks/gps/{provider}               (public, HMAC)
```

**Tableaux de bord et rapports**
```
GET    /api/v1/dashboard/operational                 (ROLE_ADMIN, ROLE_MANAGER)
GET    /api/v1/dashboard/executive                   (ROLE_PDG, ROLE_ADMIN)
GET    /api/v1/reports/financial?from=&to=&groupBy=MONTH
GET    /api/v1/reports/profitability?dimension=VEHICLE|DRIVER|MISSION|AGENCY
GET    /api/v1/reports/export?type=&format=CSV|XLSX|PDF
```

**Administration**
```
GET|POST /api/v1/users        PATCH /api/v1/users/{id}/status
POST     /api/v1/users/{id}/reset-password
GET|POST /api/v1/agencies
GET|POST /api/v1/clients      GET|POST /api/v1/service-types
GET|PUT  /api/v1/settings
GET|POST /api/v1/integrations
GET      /api/v1/audit-logs
```

## 8.5 Documentation

Swagger UI exposé sur `/swagger-ui.html` en développement uniquement, avec authentification Bearer configurée. Chaque DTO documenté par `@Schema`.

---

# 9. Temps réel (WebSocket STOMP)

## 9.1 Configuration

- Endpoint de connexion : `/ws`, avec repli SockJS.
- Broker simple en mémoire (`enableSimpleBroker`) — suffisant pour un déploiement mono-instance. Passage à un relais externe uniquement en cas de montée en charge multi-instances.
- **Authentification à la connexion** : le JWT est transmis dans l'en-tête STOMP `CONNECT` et validé par un `ChannelInterceptor`. Une connexion non authentifiée est rejetée.

## 9.2 Canaux publiés

| Canal | Contenu | Fréquence |
|---|---|---|
| `/topic/vehicle-positions` | Position de tous les véhicules | À chaque webhook, débit limité à 1 message / véhicule / 10 s |
| `/topic/vehicle-positions/{vehicleId}` | Position d'un véhicule précis | Idem |
| `/topic/alerts` | Nouvelle alerte créée | Événementiel |
| `/topic/alerts/critical` | Alertes de sévérité critique | Événementiel |
| `/topic/missions/{missionId}/progress` | Avancement d'une mission | À chaque mise à jour |
| `/topic/dashboard/kpi` | Rafraîchissement des compteurs | Toutes les 30 s |
| `/user/queue/notifications` | Notification personnelle | Événementiel |

La diffusion est déclenchée par un `@EventListener` sur les événements applicatifs, jamais depuis un service métier directement.

---

# 10. Ingestion GPS par webhook

## 10.1 Chaîne de traitement

```
Boîtier télématique
      │  HTTPS POST (JSON)
      ▼
POST /api/v1/webhooks/gps/{provider}
      │
      ├─► Vérification HMAC + horodatage       → rejet 401
      ├─► Contrôle d'idempotence (Redis)       → 200 "DOUBLON"
      ├─► Validation du format                 → rejet 422
      ├─► Enregistrement brut (webhook_events)
      ├─► Réponse HTTP 202 immédiate  ◄── ne jamais faire attendre le boîtier
      │
      └─► Traitement asynchrone (@Async, pool dédié)
            ├─ Mise à jour du cache Redis (dernière position)
            ├─ Mise en file d'écriture par lots vers gps_positions
            ├─ Calcul de distance incrémentale (Haversine) → vehicles.current_kilometers
            ├─ Évaluation des règles d'alerte (AlertEngine)
            └─ Publication WebSocket (avec limitation de débit)
```

**Principe directeur :** le webhook accuse réception en moins de 200 ms. Tout traitement métier est asynchrone. Un traitement lent provoquerait des rejeux côté prestataire et une saturation.

## 10.2 Contrat d'entrée attendu

```json
{
  "deviceId": "TRK-00457",
  "timestamp": "2026-08-02T09:31:22Z",
  "latitude": 4.0511,
  "longitude": 9.7679,
  "speed": 62.5,
  "heading": 178.0,
  "altitude": 13.0,
  "ignition": true,
  "odometer": 128450.2,
  "fuelLevel": 68.5,
  "events": ["OVERSPEED"]
}
```

Un adaptateur par prestataire (`GpsPayloadAdapter`) normalise le format vendeur vers ce modèle canonique. Le paramètre `{provider}` de l'URL sélectionne l'adaptateur — l'ajout d'un second prestataire ne modifie aucun code existant.

## 10.3 Écriture par lots

Les positions sont accumulées en mémoire et écrites par `JdbcTemplate.batchUpdate()` toutes les 5 secondes ou tous les 100 enregistrements. Passer par JPA position par position serait un goulot d'étranglement inutile sur une table à fort débit.

## 10.4 Volumétrie réelle

Calcul pour **11 camions** (décision D11), à raison d'une trame toutes les 30 s en mouvement et toutes les 5 min à l'arrêt, sur une base de 10 h de roulage par jour ouvré :

```
Par camion et par jour   : 1 200 trames en roulage + 170 à l'arrêt ≈ 1 370
Parc complet, par jour   : 11 × 1 370 ≈ 15 000 trames
Par mois                 : ≈ 450 000 lignes
Sur 90 jours (rétention) : ≈ 1,35 million de lignes, soit ~200 Mo
```

**Conclusion : la volumétrie est faible.** Une table indexée classique suffirait techniquement. Le partitionnement est néanmoins conservé, pour une seule raison : il rend la purge des 90 jours instantanée et sans verrou.

## 10.5 Partitionnement et rétention (décision D6)

```sql
CREATE TABLE gps_positions (...) PARTITION BY RANGE (recorded_at);
-- une partition par mois, créée à l'avance par tâche planifiée
```

Tâche quotidienne à 02h00 :
1. Agrégation des positions de J-91 dans `gps_daily_stats` (distance, vitesse max et moyenne, temps de roulage et d'arrêt).
2. `DROP` de la partition entièrement antérieure à 90 jours — instantané, contrairement à un `DELETE` massif.

Les analyses historiques au-delà de 90 jours s'appuient exclusivement sur `gps_daily_stats`, dont la volumétrie est négligeable (11 × 365 ≈ 4 000 lignes par an).

## 10.6 Simulateur de trames (décision D10)

Le fournisseur de boîtiers étant communiqué au sprint 5, le développement de la chaîne d'ingestion ne doit **pas** l'attendre. Un simulateur est développé dès le sprint 5 :

- Composant `GpsSimulator` activé par `@Profile("dev")`.
- Rejoue des trajets réalistes sur les axes Douala–Yaoundé, Douala–Bafoussam et intra-Douala, à partir de traces GeoJSON.
- Émet vers le webhook réel, signature HMAC comprise, afin de tester la chaîne complète.
- Permet de déclencher volontairement chaque type d'alerte (excès de vitesse, arrêt anormal, chute de niveau de carburant).

Lorsque le fournisseur réel sera connu, seule une classe `GpsPayloadAdapter` sera à écrire pour normaliser son format vers le modèle canonique. Aucun autre code n'est impacté.

## 10.5 Moteur d'alertes

Interface `AlertRuleEvaluator` implémentée par une classe par type de règle (pattern Strategy), paramétrée depuis `alert_rules` :

| Règle | Déclenchement | Sévérité |
|---|---|---|
| Vitesse excessive | `speed > seuil` (défaut 110 km/h) | ÉLEVÉ |
| Démarrage non autorisé | `ignition = true` hors mission planifiée ou hors plage horaire | CRITIQUE |
| Immobilisation anormale | Aucune position depuis N minutes en cours de mission | MOYEN |
| Sortie de zone | Écart supérieur à N km de l'itinéraire | MOYEN |
| Surconsommation | Consommation calculée > moyenne du véhicule + 20 % | ÉLEVÉ |
| Chute de niveau carburant | Baisse brutale hors ravitaillement (siphonnage) | CRITIQUE |
| Assurance échue | Tâche planifiée quotidienne | CRITIQUE |
| Visite technique à échéance | Tâche planifiée, J-30 | ÉLEVÉ |
| Permis à échéance | Tâche planifiée, J-30 | MOYEN |
| Maintenance préventive | Seuil de kilométrage franchi | MOYEN |

**Anti-spam :** aucune alerte identique (même véhicule, même type) n'est recréée dans une fenêtre de 30 minutes ; le compteur d'occurrences de l'alerte existante est incrémenté.

---

# 11. Cartographie (décision D3)

## 11.1 Séparation des responsabilités

| Besoin | Fournisseur v1 | Remplacement Google |
|---|---|---|
| Source des positions | Boîtier télématique via webhook | *Inchangé — indépendant de la carte* |
| Fond de carte | Tuiles OSM via Leaflet (frontend) | Google Maps JavaScript API |
| Géocodage adresse → coordonnées | Nominatim | Geocoding API |
| Géocodage inverse | Nominatim | Reverse Geocoding API |
| Calcul d'itinéraire et de distance | OSRM (public ou autohébergé) | Directions API |

## 11.2 Abstraction backend

```java
public interface MapProvider {
    GeoPoint geocode(String address);
    String reverseGeocode(double lat, double lng);
    RouteResult route(GeoPoint origin, GeoPoint destination);
    double distanceKm(GeoPoint a, GeoPoint b);
}
```

Deux implémentations : `OsmMapProvider` (`@ConditionalOnProperty(name="map.provider", havingValue="osm", matchIfMissing=true)`) et `GoogleMapProvider`. La bascule se fait par une seule ligne de configuration, sans recompilation du métier.

## 11.3 Contraintes propres à OSM

- **Nominatim public** : maximum 1 requête par seconde, en-tête `User-Agent` identifiant l'application obligatoire, usage massif interdit. → Mise en cache Redis de tous les géocodages (TTL 30 jours) et file d'attente à débit limité côté backend.
- **Tuiles `tile.openstreetmap.org`** : réservées à un usage modéré. Pour une application professionnelle, prévoir un fournisseur de tuiles dédié (MapTiler, Thunderforest, Stadia Maps) ou l'autohébergement.
- **OSRM public** : sans garantie de service. Autohébergement recommandé dès la mise en production (conteneur Docker + extrait OSM du Cameroun).
- La couverture OSM du Cameroun est bonne sur les axes principaux, plus inégale sur les pistes secondaires — à vérifier sur les trajets réels de SOGECO.

## 11.4 Frontend

Leaflet + react-leaflet, marqueurs colorés par statut, tracé d'itinéraire par polyligne, mise à jour des positions par abonnement STOMP.

**Cadrage adapté au périmètre :** avec 11 véhicules, le regroupement de marqueurs (*clustering*) est inutile — chaque camion reste lisible individuellement. Vue par défaut centrée sur Douala (environ 4,05 N / 9,77 E, zoom 12), avec un bouton de recadrage automatique sur l'ensemble du parc et un mode « vue Cameroun » pour suivre les missions inter-villes en cours. Les 4 agences sont affichées en permanence avec une icône distincte de celle des camions.

**Impact sur les quotas :** à 11 véhicules et une dizaine d'utilisateurs, la consommation de tuiles OSM et de requêtes Nominatim reste très en dessous des seuils critiques. Le cache de géocodage suffit largement en v1 ; un fournisseur de tuiles dédié ne deviendra nécessaire qu'en cas d'extension importante du parc.

---

# 12. Règles métier et calculs

## 12.1 Consommation de carburant

```
Consommation (L/100km) = quantité du plein × 100 / (km actuel − km du plein précédent)
```
Calculé uniquement entre deux pleins complets (`full_tank = true`). Un plein partiel est stocké mais exclu du calcul. Anomalie signalée si l'écart dépasse 20 % de la moyenne mobile sur 5 pleins du véhicule.

## 12.2 Financier (décision D1)

```
Marge par mission   = revenue_amount − fuel_cost − other_cost
Coût carburant      = somme des fuel_logs rattachés à la mission
CA mensuel          = Σ revenue_amount des missions TERMINE de la période
Dépenses mensuelles = carburant + maintenance + expenses (dont salaires)
Bénéfice net        = CA − dépenses totales
Marge brute (%)     = (CA − coûts directs) / CA × 100
```

Règles de saisie : le CA est obligatoire à la clôture d'une mission (`POST /missions/{id}/complete`), modifiable ensuite par `ROLE_ADMIN` uniquement, toute modification étant tracée dans `audit_logs`.

## 12.3 Indicateurs de flotte

```
Taux d'utilisation  = véhicules en mission / véhicules actifs × 100
Taux de disponibilité = 1 − (jours d'immobilisation / jours × véhicules)
Taux de résolution  = alertes RESOLUE / alertes créées sur la période × 100
Coût au kilomètre   = (carburant + maintenance + assurance) / km parcourus
```

## 12.4 Champs dénormalisés

`drivers.average_rating`, `drivers.total_revenue_generated`, `vehicles.avg_fuel_consumption`, `vehicles.current_kilometers` sont des **caches**, jamais des sources de vérité. Recalcul par événement applicatif à chaque écriture concernée, plus tâche de réconciliation nocturne.

---

# 13. Tâches planifiées

| Tâche | Fréquence | Rôle |
|---|---|---|
| Purge et agrégation GPS | Quotidienne, 02h00 | Décision D6 |
| Création des partitions du mois suivant | Mensuelle, le 25 | Anticipation |
| Contrôle des échéances (assurances, visites, permis) | Quotidienne, 06h00 | Génération d'alertes |
| Recalcul des agrégats dénormalisés | Quotidienne, 03h00 | Réconciliation |
| Pré-calcul des KPI de dashboard | Toutes les 15 min | Performance |
| Détection des anomalies de consommation | Quotidienne, 04h00 | Alertes |
| Purge des refresh tokens expirés | Quotidienne | Hygiène |
| Purge de `webhook_events` (> 30 jours) | Hebdomadaire | Volumétrie |
| Sauvegarde PostgreSQL (`pg_dump`) | Quotidienne, 01h00 | Rétention 30 jours |

Verrouillage par ShedLock si un déploiement multi-instances est envisagé.

---

# 14. Observabilité

- **Journalisation** Logback, format JSON en production, niveau `INFO` (`DEBUG` sur `com.sogeco` en développement). Un `traceId` par requête via MDC.
- Interdiction absolue de journaliser mots de passe, jetons, signatures ou coordonnées GPS nominatives en clair.
- **Actuator** : `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus`, exposés sur un port interne, protégés par `ROLE_ADMIN`.
- Métriques métier via Micrometer : trames GPS reçues et rejetées, alertes générées, latence des endpoints, taille des lots d'écriture.
- Rotation des journaux : 30 jours, archivage compressé.

---

# 15. Tests

| Niveau | Cible | Outils |
|---|---|---|
| Unitaire | Services, calculateurs, moteur d'alertes | JUnit 5 + Mockito |
| Intégration | Repositories, requêtes complexes | Testcontainers PostgreSQL |
| API | Contrôleurs, sécurité, codes de retour | MockMvc + `@WithMockUser` |
| Bout en bout | Ingestion webhook → alerte → WebSocket | Testcontainers + client STOMP |

**Priorités de couverture :** calcul de consommation, calculs financiers, moteur d'alertes, transitions d'état des missions, sécurité des webhooks, filtrage par rôle. Objectif de 70 % sur la couche service, non négociable sur les calculs financiers.

Jeu de données de démonstration via `V999__seed_dev_data.sql`, chargé sur le profil `dev` uniquement.

---

# 16. Environnements et déploiement

## 16.1 Profils

| Profil | Base | Journalisation | Swagger | CORS |
|---|---|---|---|---|
| `dev` | PostgreSQL local Docker | DEBUG | Activé | `localhost:5173` |
| `test` | Testcontainers | INFO | Désactivé | — |
| `prod` | PostgreSQL dédié | INFO/JSON | Désactivé | Domaine client |

## 16.2 Docker Compose (développement)

```yaml
services:
  postgres:   # image postgres:16-alpine, volume persistant, port 5432
  redis:      # image redis:7-alpine, port 6379
  backend:    # build local, dépend de postgres et redis, profil dev
  pgadmin:    # optionnel, confort de développement
```

## 16.3 Intégration continue

Sur chaque `push` : compilation Maven → tests unitaires → tests d'intégration Testcontainers → contrôle Spotless → rapport JaCoCo → construction de l'image Docker sur la branche `main`.

## 16.4 Production

Nginx en frontal (TLS, compression gzip, service des fichiers statiques React), backend en conteneur, PostgreSQL avec sauvegarde quotidienne testée en restauration, variables sensibles injectées par l'environnement.

**Dimensionnement serveur (11 camions, 10 utilisateurs) :** un VPS unique de **2 vCPU / 4 Go de RAM / 50 Go SSD** est suffisant et confortable. Répartition indicative : 1 Go pour la JVM (`-Xmx1g`), 1 Go pour PostgreSQL, 256 Mo pour Redis, le reste pour le système et le cache disque. Aucune haute disponibilité, aucun équilibrage de charge, aucune réplication de base ne sont justifiés à cette échelle — la sauvegarde quotidienne testée constitue la seule mesure de continuité nécessaire.

---

# 17. Exigences non fonctionnelles

| Exigence | Cible |
|---|---|
| Temps de réponse API (95e centile) | < 500 ms |
| Chargement d'un dashboard | < 2 s |
| Accusé de réception webhook GPS | < 200 ms |
| Latence de diffusion WebSocket | < 3 s |
| Utilisateurs simultanés | 10 (dimensionné pour 50) |
| Véhicules suivis | 11 (extensible à 100 sans réécriture) |
| Agences | 4 |
| Fréquence des trames GPS | 1 / 30 s en mouvement, 1 / 5 min à l'arrêt |
| Volumétrie GPS | ≈ 450 000 lignes / mois, ≈ 200 Mo sur 90 jours |
| Taille totale de la base à 1 an | < 2 Go |
| Disponibilité | 99 % en heures ouvrées |
| Objectif de point de reprise (RPO) | 24 h |
| Navigateurs | Chrome, Edge, Firefox, Safari — 2 dernières versions |

---

# 18. Roadmap de développement

| Sprint | Durée | Contenu | Livrable |
|---|---|---|---|
| **S0** | 3 j | Initialisation projet, Docker Compose, Flyway, `BaseEntity`, gestion globale des erreurs, Swagger | Squelette qui démarre |
| **S1** | 1 sem | Sécurité JWT, `users`, `agencies`, rôles, `audit_logs`, `system_settings` | Authentification fonctionnelle |
| **S2** | 1 sem | `vehicles`, `drivers`, `vehicle_assignments`, `driver_ratings`, `documents` | CRUD parc et RH |
| **S3** | 1 sem | `clients`, `service_types`, `missions`, cycle de vie, saisie du CA | Module logistique |
| **S4** | 1 sem | `partners`, `fuel_logs`, `maintenance_logs`, `maintenance_items`, `expenses`, calculs de consommation | Contrôle des coûts |
| **S5** | 1,5 sem | Webhook GPS, `gps_positions` partitionné, Redis, WebSocket, `alerts`, moteur de règles | Temps réel opérationnel |
| **S6** | 1 sem | `insurance_contracts`, `technical_inspections`, `insurance_claims`, échéancier, dashboard opérationnel | Conformité |
| **S7** | 1 sem | Dashboard PDG, rapports financiers, exports CSV/XLSX/PDF, `integrations` | Décisionnel |
| **S8** | 1 sem | Tests de charge, optimisation des requêtes, durcissement sécurité, documentation, préparation production | Backend livrable |

**Total : environ 9 semaines de backend.** Le planning initial de 7 semaines couvrait backend *et* frontend, ce qui n'est pas réaliste sur ce périmètre en développement solo.

---

# 19. Risques identifiés

| Risque | Impact | Probabilité | Parade |
|---|---|---|---|
| Prestataire de boîtiers GPS communiqué tardivement (D10) | Format des trames inconnu jusqu'au sprint 5 | Certaine (planifiée) | Simulateur de trames + adaptateur par prestataire : le développement se poursuit sans attendre |
| Quotas Nominatim / tuiles OSM atteints | Dégradation de la carte | **Faible** (11 véhicules) | Cache Redis des géocodages ; fournisseur de tuiles dédié seulement si le parc s'étend |
| Volumétrie GPS mal anticipée | Saturation disque | **Faible** (< 2 Go / an) | Partitionnement et purge à 90 jours, supervision de la taille des tables |
| Qualité du réseau mobile au Cameroun | Trames perdues | Élevée | Tolérance aux trames désordonnées, boîtiers avec mémoire tampon |
| Saisie manuelle du CA non réalisée par les opérateurs | Dashboard PDG vide | Élevée | CA obligatoire à la clôture de mission + alerte sur missions terminées sans CA |
| Périmètre fonctionnel très large pour un développeur seul | Retard | Élevée | Livraison par sprint avec validation client à chaque fin de sprint |
| Coordonnées d'agences et de clients non fournies | Carte incomplète | Moyenne | Géocodage automatique à la création + correction manuelle possible |

---

# 20. Points restant à valider avec le client

**Résolus :** périmètre (11 camions, direction Douala, 4 agences), mode de saisie du CA, gestion des rôles, cartographie, protocole d'intégration, comptes chauffeurs, rétention GPS, fournisseur télématique (communiqué au sprint 5).

**En attente :**

1. Coordonnées et adresses précises des 4 agences (BP Cité, Ndogsimbi, Mboppi, Ndokoti), pour l'initialisation de `V2__seed_agencies.sql`.
2. Fiches des 11 camions : immatriculation, châssis, marque, modèle, type de carrosserie, kilométrage actuel, agence de rattachement.
3. Liste des chauffeurs et de leurs affectations actuelles.
4. Grille tarifaire et types de prestations à créer dans `service_types`.
5. Nombre moyen de missions par mois (dimensionne les rapports, pas l'architecture).
6. Mode de saisie des salaires (manuel ou import).
7. Hébergement retenu : serveur client, VPS ou cloud.
8. Modalités d'information des chauffeurs sur la géolocalisation.
