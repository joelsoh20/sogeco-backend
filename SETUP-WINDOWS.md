# Installation sous Windows (PowerShell)

## 1. Prerequis

| Outil | Verification |
|---|---|
| Java 25 (LTS) | `java -version` |
| Docker Desktop | `docker --version` — doit etre **demarre** |
| IntelliJ IDEA | Maven est integre, aucune installation separee necessaire |

Si Java 25 n'est pas installe : https://adoptium.net/temurin/releases/?version=25
**LTS** : version recommandee pour la duree de vie du projet.

## 2. Creer le fichier .env

PowerShell masque les fichiers commencant par un point, et ils ne sont pas
toujours inclus dans un telechargement. Renommer le fichier fourni :

```powershell
Rename-Item env.example.txt .env
```

Ou le creer directement :

```powershell
@"
DB_NAME=sogeco_fleet
DB_USER=sogeco
DB_PASSWORD=sogeco_dev_password
REDIS_PASSWORD=sogeco_redis_password
SPRING_PROFILES_ACTIVE=dev
SERVER_PORT=8080
JWT_SECRET=CHANGER_CETTE_VALEUR_EN_PRODUCTION_MINIMUM_256_BITS
JWT_ACCESS_TOKEN_MINUTES=15
JWT_REFRESH_TOKEN_DAYS=7
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
GOOGLE_ALLOWED_DOMAIN=sogeco.cm
TELEMATICS_WEBHOOK_SECRET=
"@ | Out-File -FilePath .env -Encoding utf8
```

Verifier : `Get-ChildItem -Force | Select-Object Name`

## 3. Demarrer l'infrastructure

```powershell
docker compose up -d
docker compose ps        # postgres et redis doivent etre "healthy"
```

## 4. Lancer l'application

### Option A — depuis IntelliJ (recommandee, aucune installation)

1. File > Open > selectionner **pom.xml** > *Open as Project*
2. Attendre la fin du telechargement des dependances (barre de statut)
3. Ouvrir `SogecoFleetApplication.java`
4. Cliquer sur la fleche verte a cote de `public class SogecoFleetApplication`

### Option B — generer le wrapper Maven depuis IntelliJ

Dans le terminal integre d'IntelliJ (Alt+F12), qui connait le Maven embarque :

```powershell
mvn -N wrapper:wrapper -Dmaven=3.9.9
```

Le wrapper est ensuite disponible pour toute la suite du projet :

```powershell
.\mvnw.cmd spring-boot:run
```

### Option C — installer Maven globalement

```powershell
winget install Apache.Maven
```

Fermer et rouvrir PowerShell, puis :

```powershell
mvn spring-boot:run
```

## 5. Verifier

| Ressource | URL |
|---|---|
| Ping | http://localhost:8080/api/v1/ping |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Health | http://localhost:8080/actuator/health |

Le ping doit renvoyer :

```json
{"application":"sogeco-fleet-manager","status":"UP","currency":"XAF","timezone":"Africa/Douala","serverTime":"..."}
```

## Notes PowerShell

- `./mvnw` ne fonctionne pas : sous Windows, c'est `.\mvnw.cmd`
- Les fichiers `.env` et `.gitignore` existent mais sont masques : utiliser `ls -Force`
- Si le port 5432 est deja pris (PostgreSQL local installe), modifier le mapping
  dans `docker-compose.yml` : `"5433:5432"`, puis ajouter `DB_PORT=5433` dans `.env`

## Erreurs courantes

| Message | Cause | Solution |
|---|---|---|
| `Cannot connect to the Docker daemon` | Docker Desktop non demarre | Lancer Docker Desktop |
| `Connection refused: localhost:5432` | Conteneurs non demarres | `docker compose up -d` |
| `invalid target release: 26` | JDK inferieur a 26 | Installer le JDK 26, puis File > Project Structure > SDK |
| `port is already allocated` | Port 5432 ou 6379 occupe | Changer le mapping dans docker-compose.yml |
