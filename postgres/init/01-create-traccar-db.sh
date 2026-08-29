#!/bin/sh
# Execute une seule fois par l'image postgres, au tout premier demarrage
# (volume postgres_data vide). Cree la base dediee a Traccar, en plus de
# celle de l'application (POSTGRES_DB) creee automatiquement par l'image.
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE DATABASE traccar OWNER $POSTGRES_USER;
EOSQL
