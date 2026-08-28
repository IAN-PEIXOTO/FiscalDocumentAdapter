#!/usr/bin/env bash
# Backup do banco de producao/homologacao (FIS-26/33).
#
# Faz um dump logico completo (pg_dump -Fc, formato custom - permite restore
# seletivo/paralelo) e comprime. Nao envia para nenhum storage remoto
# sozinho - isso e responsabilidade de quem agenda a execucao (cron, CI
# agendado, etc.): rode este script e depois copie o arquivo gerado para
# onde a politica de retencao da empresa mandar (S3, bucket da nuvem
# escolhida, etc.). Deliberadamente sem dependencia de nenhum provedor
# especifico de nuvem.
#
# Variaveis de ambiente esperadas (mesmas usadas pela aplicacao, ver
# application-prod.yml/application-homolog.yml):
#   DATABASE_URL       - no formato jdbc:postgresql://host:porta/banco
#   DATABASE_USERNAME
#   DATABASE_PASSWORD
#
# Uso: ./scripts/backup-postgres.sh [diretorio-de-saida]
# (default do diretorio de saida: ./backups)

set -euo pipefail

DIRETORIO_SAIDA="${1:-./backups}"
mkdir -p "$DIRETORIO_SAIDA"

if [[ -z "${DATABASE_URL:-}" || -z "${DATABASE_USERNAME:-}" || -z "${DATABASE_PASSWORD:-}" ]]; then
    echo "Erro: defina DATABASE_URL, DATABASE_USERNAME e DATABASE_PASSWORD antes de rodar este script." >&2
    exit 1
fi

# Extrai host/porta/banco de uma URL JDBC no formato jdbc:postgresql://host:porta/banco
SEM_PREFIXO="${DATABASE_URL#jdbc:postgresql://}"
HOST_PORTA="${SEM_PREFIXO%%/*}"
BANCO="${SEM_PREFIXO#*/}"
BANCO="${BANCO%%\?*}" # remove query params (ex.: ?sslmode=require), se houver
HOST="${HOST_PORTA%%:*}"
PORTA="${HOST_PORTA#*:}"

CARIMBO_DATA="$(date -u +%Y%m%dT%H%M%SZ)"
ARQUIVO_SAIDA="$DIRETORIO_SAIDA/fiscaladapter-${CARIMBO_DATA}.dump"

echo "Fazendo backup de $BANCO em $HOST:$PORTA -> $ARQUIVO_SAIDA"

PGPASSWORD="$DATABASE_PASSWORD" pg_dump \
    --host="$HOST" \
    --port="$PORTA" \
    --username="$DATABASE_USERNAME" \
    --dbname="$BANCO" \
    --format=custom \
    --file="$ARQUIVO_SAIDA"

echo "Backup concluido: $ARQUIVO_SAIDA ($(du -h "$ARQUIVO_SAIDA" | cut -f1))"
