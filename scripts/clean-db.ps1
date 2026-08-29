# Vide les donnees METIER de la base sogeco_fleet (TRUNCATE), sans toucher
# au schema, a l'historique Flyway, ni aux comptes/roles/permissions/
# parametres systeme (seed V2) : le compte admin@sogeco.cm reste utilisable.
# A executer avec le conteneur postgres demarre (docker compose up -d postgres).
#
# Usage :
#   .\scripts\clean-db.ps1          -> demande confirmation
#   .\scripts\clean-db.ps1 -Force   -> sans confirmation

param(
    [switch]$Force
)

$ContainerName = if ($env:DB_CONTAINER) { $env:DB_CONTAINER } else { "sogeco-postgres" }
$DbUser = if ($env:DB_USER) { $env:DB_USER } else { "sogeco" }
$DbName = if ($env:DB_NAME) { $env:DB_NAME } else { "sogeco_fleet" }
$SqlFile = Join-Path $PSScriptRoot "clean-db.sql"

$running = docker ps --filter "name=$ContainerName" --format "{{.Names}}"
if (-not $running) {
    Write-Error "Le conteneur '$ContainerName' n'est pas demarre (docker compose up -d postgres)."
    exit 1
}

if (-not $Force) {
    $confirm = Read-Host "Ceci va VIDER les tables de donnees metier de '$DbName' (donnees perdues, comptes/roles preserves). Continuer ? (o/N)"
    if ($confirm -ne "o" -and $confirm -ne "O") {
        Write-Host "Annule."
        exit 0
    }
}

Get-Content $SqlFile -Raw | docker exec -i $ContainerName psql -U $DbUser -d $DbName
if ($LASTEXITCODE -eq 0) {
    Write-Host "Base '$DbName' nettoyee (donnees videes, schema conserve)."
} else {
    Write-Error "Echec du nettoyage de la base."
    exit $LASTEXITCODE
}
