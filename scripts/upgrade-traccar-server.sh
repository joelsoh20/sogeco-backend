#!/usr/bin/env bash
# Mise a jour de Traccar sur le serveur Linux (installation native, systemd,
# /opt/traccar) -- PAS l'installation Docker de docker-compose.yml.
#
# Usage, sur le serveur :  sudo bash upgrade-traccar-server.sh [version]
#   version : defaut 6.17.0 (derniere stable verifiee le 2026-11-03)
#
# Ce que fait le script : sauvegarde (conf + base PostgreSQL), arret du service,
# installation de la nouvelle version par-dessus, redemarrage, verification.
# Il NE TOUCHE PAS a /opt/traccar/conf/traccar.xml : celui du serveur differe de
# celui du depot (hote de base de donnees, URL des webhooks), ne jamais le
# remplacer par traccar/conf/traccar.xml du depot, prevu pour Docker.
set -euo pipefail

VERSION="${1:-6.17.0}"
TRACCAR_DIR=/opt/traccar
BACKUP_DIR="/root/traccar-backup-$(date +%Y%m%d-%H%M%S)"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

[ "$(id -u)" -eq 0 ] || { echo "A lancer avec sudo." >&2; exit 1; }
command -v unzip >/dev/null || { echo "Installer unzip (apt install unzip)." >&2; exit 1; }

echo "== Version actuelle =="
ls "$TRACCAR_DIR"/lib 2>/dev/null | grep -i '^tracker-server' || true

echo "== Sauvegarde dans $BACKUP_DIR =="
mkdir -p "$BACKUP_DIR"
cp -a "$TRACCAR_DIR/conf" "$BACKUP_DIR/conf"
# Base de donnees : lue depuis traccar.xml pour ne rien supposer.
DB_URL=$(grep -oP "database.url'>\K[^<]+" "$TRACCAR_DIR/conf/traccar.xml" || true)
DB_USER=$(grep -oP "database.user'>\K[^<]+" "$TRACCAR_DIR/conf/traccar.xml" || true)
DB_PASS=$(grep -oP "database.password'>\K[^<]+" "$TRACCAR_DIR/conf/traccar.xml" || true)
case "$DB_URL" in
  jdbc:postgresql://*)
    PGPASSWORD="$DB_PASS" pg_dump "${DB_URL#jdbc:}" -U "$DB_USER" -Fc -f "$BACKUP_DIR/traccar.dump" \
      && echo "Base sauvegardee : $BACKUP_DIR/traccar.dump" \
      || { echo "ECHEC de la sauvegarde de la base : arret." >&2; exit 1; } ;;
  jdbc:h2:*)
    cp -a "$TRACCAR_DIR/data" "$BACKUP_DIR/data"; echo "Base H2 copiee." ;;
  *) echo "Base inconnue ($DB_URL) : sauvegardez-la a la main puis relancez." >&2; exit 1 ;;
esac

echo "== Telechargement de la version $VERSION =="
curl -fL -o "$WORK_DIR/traccar.zip" \
  "https://github.com/traccar/traccar/releases/download/v${VERSION}/traccar-linux-64-${VERSION}.zip"
unzip -q "$WORK_DIR/traccar.zip" -d "$WORK_DIR"

echo "== Installation =="
systemctl stop traccar
( cd "$WORK_DIR" && ./traccar.run )   # conserve conf/traccar.xml existant
systemctl daemon-reload
systemctl start traccar

echo "== Verification (20 s) =="
sleep 20
systemctl is-active traccar
ls "$TRACCAR_DIR"/lib | grep -i '^tracker-server' || true
tail -n 15 "$TRACCAR_DIR/logs/tracker-server.log"
echo
echo "Si le service ne demarre pas : restaurer avec"
echo "  systemctl stop traccar && cp -a $BACKUP_DIR/conf/. $TRACCAR_DIR/conf/"
echo "  puis pg_restore de $BACKUP_DIR/traccar.dump si la base a ete migree."
