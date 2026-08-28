#!/usr/bin/env bash
# Restaura um dump gerado por backup-postgres.sh (FIS-26/33).
#
# ATENCAO: isto sobrescreve o conteudo do banco de destino. Use um banco
# vazio ou recem-criado para restaurar, nunca aponte direto para producao
# sem antes confirmar que e essa mesma a intencao.
#
# Uso: ./scripts/restore-postgres.sh caminho/para/arquivo.dump
# Mesmas variaveis de ambiente do backup-postgres.sh (DATABASE_URL,
# DATABASE_USERNAME, DATABASE_PASSWORD) - apontando para o banco DE DESTINO.

set -euo pipefail

ARQUIVO_DUMP="${1:?Uso: $0 caminho/para/arquivo.dump}"

if [[ ! -f "$ARQUIVO_DUMP" ]]; then
    echo "Erro: arquivo de dump nao encontrado: $ARQUIVO_DUMP" >&2
    exit 1
fi

if [[ -z "${DATABASE_URL:-}" || -z "${DATABASE_USERNAME:-}" || -z "${DATABASE_PASSWORD:-}" ]]; then
    echo "Erro: defina DATABASE_URL, DATABASE_USERNAME e DATABASE_PASSWORD (do banco de DESTINO) antes de rodar este script." >&2
    exit 1
fi

SEM_PREFIXO="${DATABASE_URL#jdbc:postgresql://}"
HOST_PORTA="${SEM_PREFIXO%%/*}"
BANCO="${SEM_PREFIXO#*/}"
BANCO="${BANCO%%\?*}"
HOST="${HOST_PORTA%%:*}"
PORTA="${HOST_PORTA#*:}"

echo "Restaurando $ARQUIVO_DUMP em $BANCO ($HOST:$PORTA) - isso sobrescreve dados existentes."
read -r -p "Confirma? (digite 'sim' para continuar) " CONFIRMACAO
if [[ "$CONFIRMACAO" != "sim" ]]; then
    echo "Cancelado."
    exit 1
fi

PGPASSWORD="$DATABASE_PASSWORD" pg_restore \
    --host="$HOST" \
    --port="$PORTA" \
    --username="$DATABASE_USERNAME" \
    --dbname="$BANCO" \
    --clean \
    --if-exists \
    --no-owner \
    "$ARQUIVO_DUMP"

echo "Restore concluido."
