#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PG_DIST_DIR="$ROOT_DIR/.postgres-dist/percona-postgresql16"
PG_DATA_DIR="$ROOT_DIR/.postgres-data"
PG_RUN_DIR="$ROOT_DIR/.postgres-run"
PG_LOG_DIR="$ROOT_DIR/.postgres-logs"
PG_LOG_FILE="$PG_LOG_DIR/postgres.log"

export LD_LIBRARY_PATH="$ROOT_DIR/.postgres-dist/percona-postgresql16/lib:$ROOT_DIR/.postgres-dist/percona-python3/lib:$ROOT_DIR/.postgres-dist/percona-perl/lib:$ROOT_DIR/.postgres-dist/percona-tcl/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"

mkdir -p "$PG_RUN_DIR" "$PG_LOG_DIR"

"$PG_DIST_DIR/bin/pg_ctl" \
  -D "$PG_DATA_DIR" \
  -l "$PG_LOG_FILE" \
  -o "-h 127.0.0.1 -p 5432 -k $PG_RUN_DIR" \
  start
