#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE USER $POSTGRES_READ_USER WITH PASSWORD '$POSTGRES_READ_USER_PASSWORD';
    GRANT SELECT ON ALL TABLES IN SCHEMA public to $POSTGRES_READ_USER;
EOSQL