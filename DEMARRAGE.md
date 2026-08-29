# DÉMARRAGE — Outils et génération du projet

Procédure complète, dans l'ordre. Compter 45 minutes la première fois.

---

# ÉTAPE 1 — Installer les outils

Ces quatre outils ne viennent pas du site Spring : ils s'installent séparément.

| Outil | Rôle | Source |
|---|---|---|
| **JDK 25 (LTS)** | Compiler et exécuter | https://adoptium.net/temurin/releases/?version=25 |
| **IntelliJ IDEA** | IDE (Community suffit) | https://www.jetbrains.com/idea/download |
| **Docker Desktop** | PostgreSQL + Redis | https://www.docker.com/products/docker-desktop |
| **Git** | Versionnement | https://git-scm.com/download/win |

**Maven n'est pas à installer** : IntelliJ l'embarque, et le projet généré fournira son propre wrapper.

## Vérification (PowerShell, après avoir rouvert le terminal)

```powershell
java -version      # doit afficher 25.x
docker --version
git --version
```

Si `java -version` affiche une version inférieure à 25, ou rien du tout, vérifier que
`JAVA_HOME` pointe bien sur le JDK 25 et que `%JAVA_HOME%\bin` est dans le `Path`.

> **JDK 25 est une version LTS**, avec une periode de support longue adaptee a la
> production.
> Choix assumé du projet ; prévoir de re-migrer vers la prochaine LTS (25 ou 29) à terme.

**Docker Desktop doit être lancé** avant tout `docker compose`. Sur Windows Home,
il demande WSL2 — l'installeur le propose automatiquement.

---

# ÉTAPE 2 — Générer le projet sur Spring Initializr

Aller sur **https://start.spring.io**

## Paramètres du projet

| Champ | Valeur |
|---|---|
| Project | **Maven** |
| Language | **Java** |
| Spring Boot | **4.1.x** — la version stable la plus récente proposée |
| Group | `com.sogeco` |
| Artifact | `fleet-manager` |
| Name | `fleet-manager` |
| Description | `Plateforme de gestion et de suivi de flotte de transport` |
| Package name | `com.sogeco.fleet` |
| Packaging | **Jar** |
| Java | **26** |

> **Ne pas choisir une version 3.x.** La ligne 3.5 n'est plus supportée depuis juin 2026.
> Si Initializr ne propose que des versions SNAPSHOT en 4.x, prendre la dernière release
> stable affichée sans mention `(SNAPSHOT)` ni `(M1)`.

## Dépendances à ajouter (bouton ADD DEPENDENCIES)

Rechercher et cocher exactement ces onze entrées :

| Dépendance | Pourquoi |
|---|---|
| **Spring Web** | API REST |
| **Spring Data JPA** | Persistance |
| **PostgreSQL Driver** | Base de données |
| **Flyway Migration** | Migrations versionnées |
| **Spring Data Redis** | Cache, idempotence des webhooks |
| **Spring Security** | Authentification et autorisation |
| **OAuth2 Client** | Connexion Google (sprint 1) |
| **Validation** | Validation des DTO |
| **Spring Boot Actuator** | Supervision |
| **WebSocket** | Temps réel (sprint 5) |
| **Lombok** | Réduction du code répétitif |
| **Testcontainers** | Tests d'intégration sur base réelle |

Puis **GENERATE** → un `fleet-manager.zip` est téléchargé.

## Ce que Initializr ne fournit pas

Deux bibliothèques du cahier technique ne sont pas au catalogue et devront être
ajoutées à la main dans le `pom.xml` (elles sont déjà présentes dans celui que je t'ai
fourni) :

- **MapStruct** + `lombok-mapstruct-binding` — génération des mappers Entity ↔ DTO
- **springdoc-openapi-starter-webmvc-ui version 3.1.0** — Swagger UI

> La version **3.x** de springdoc est impérative : la ligne 2.x cible Spring Boot 3
> et échoue au démarrage avec Spring Boot 4, qui embarque Jackson 3 et Spring Framework 7.

---

# ÉTAPE 3 — Fusionner avec le squelette fourni

1. **Décompresser** `fleet-manager.zip` dans
   `C:\Users\user\Desktop\sogeco_fleet_manager\backend`
   (le dossier doit contenir `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn\`, `src\`)

2. **Conserver du projet généré :** `mvnw`, `mvnw.cmd`, `.mvn\`

3. **Remplacer par les fichiers fournis :**

```
pom.xml                    → remplacer intégralement
src/main/java/...          → copier tout le dossier common/
src/main/resources/        → copier application.yml, application-dev.yml,
                             application-prod.yml, db/migration/
src/test/java/...          → copier AbstractIntegrationTest et le test de démarrage
src/test/resources/        → copier application-test.yml
```

4. **Ajouter à la racine :** `docker-compose.yml`, `Dockerfile`, `.gitignore`,
   `README.md`, et renommer `env.example.txt` en `.env`

5. **Supprimer** la classe `FleetManagerApplication.java` générée par Initializr :
   elle fait doublon avec `SogecoFleetApplication.java`.

Arborescence finale attendue :

```
backend/
├── .mvn/
├── mvnw
├── mvnw.cmd
├── .env
├── .gitignore
├── docker-compose.yml
├── Dockerfile
├── pom.xml
├── README.md
└── src/
    ├── main/java/com/sogeco/fleet/
    │   ├── SogecoFleetApplication.java
    │   └── common/{config,dto,entity,exception,util,web}
    ├── main/resources/{application*.yml, db/migration}
    └── test/...
```

---

# ÉTAPE 4 — Lancer

```powershell
cd C:\Users\user\Desktop\sogeco_fleet_manager\backend

docker compose up -d
docker compose ps          # postgres et redis doivent être "healthy"

.\mvnw.cmd spring-boot:run
```

Le wrapper télécharge Maven au premier lancement : c'est normal que ce soit long.

## Vérification

| Ressource | Attendu |
|---|---|
| http://localhost:8080/api/v1/ping | `{"status":"UP","currency":"XAF",...}` |
| http://localhost:8080/swagger-ui.html | Interface Swagger |
| http://localhost:8080/actuator/health | `{"status":"UP"}` |

---

# ÉTAPE 5 — Initialiser le dépôt Git

```powershell
git init
git add .
git commit -m "Sprint 0 : squelette technique, Docker, Flyway, socle commun"
```

Le `.gitignore` exclut `.env`, `target/` et `.idea/`. **Ne jamais versionner le `.env`.**

---

# Problèmes courants

| Message | Cause | Solution |
|---|---|---|
| `invalid target release: 21` | JDK < 21 | File → Project Structure → SDK → 21 |
| `Cannot connect to the Docker daemon` | Docker Desktop arrêté | Le lancer et attendre l'icône verte |
| `port is already allocated` | 5432 ou 6379 occupé | Mapper sur `5433:5432`, ajouter `DB_PORT=5433` au `.env` |
| `Connection refused: localhost:5432` | Conteneurs non démarrés | `docker compose up -d` |
| `NoClassDefFoundError` au démarrage | springdoc 2.x avec Spring Boot 4 | Passer springdoc en 3.1.0 |
| `Table not found` en test | Docker requis par Testcontainers | Démarrer Docker Desktop |
| `mvnw n'est pas reconnu` | Syntaxe Unix sous PowerShell | Utiliser `.\mvnw.cmd` |
