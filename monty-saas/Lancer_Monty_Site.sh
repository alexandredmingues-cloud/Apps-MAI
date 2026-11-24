#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ ! -f "$SCRIPT_DIR/launch.py" ]; then
  for CAND in "$HOME/Desktop/monty-saas" "$HOME/Bureau/monty-saas" "$HOME/Documents/monty-saas" "$HOME/monty-saas"; do
    if [ -f "$CAND/launch.py" ]; then
      SCRIPT_DIR="$CAND"
      break
    fi
  done
fi

cd "$SCRIPT_DIR"
exec python3 launch.py --auto
