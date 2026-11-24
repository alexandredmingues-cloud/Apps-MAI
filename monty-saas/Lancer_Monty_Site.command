#!/bin/bash
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
if [ ! -f "$SCRIPT_DIR/launch.py" ]; then
  for CAND in "$HOME/Desktop/monty-saas" "$HOME/Bureau/monty-saas" "$HOME/Documents/monty-saas" "$HOME/monty-saas"; do
    if [ -f "$CAND/launch.py" ]; then
      SCRIPT_DIR="$CAND"
      break
    fi
  done
fi

if [ ! -f "$SCRIPT_DIR/launch.py" ]; then
  echo "[Erreur] Dossier monty-saas introuvable. Créez un alias depuis monty-saas/Lancer_Monty_Site.command."
  read -r -p "Appuyez sur Entrée pour fermer..."
  exit 1
fi

cd "$SCRIPT_DIR" || exit 1
python3 launch.py --auto
EXIT_CODE=$?
if [ $EXIT_CODE -ne 0 ]; then
  read -r -p "Une erreur est survenue (code $EXIT_CODE). Appuyez sur Entrée..."
fi
