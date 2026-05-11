#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PG_DIST_DIR="$ROOT_DIR/.postgres-dist/percona-postgresql16"
PG_DATA_DIR="$ROOT_DIR/.postgres-data"

export LD_LIBRARY_PATH="$ROOT_DIR/.postgres-dist/percona-postgresql16/lib:$ROOT_DIR/.postgres-dist/percona-python3/lib:$ROOT_DIR/.postgres-dist/percona-perl/lib:$ROOT_DIR/.postgres-dist/percona-tcl/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"

"$PG_DIST_DIR/bin/pg_ctl" -D "$PG_DATA_DIR" stop
