#!/usr/bin/env python3
"""
Lanceur Python unifié pour la plateforme Monty Immobilier (Site Web + SaaS Pro + API FastAPI).

Exemples d'utilisation :
    python3 launch.py                           # Lance l'API Python (8000) + le Site Web Next.js (3000)
    python3 launch.py --auto                    # Mode bureau : installe si absent + lance + ouvre le navigateur
    python3 launch.py --create-desktop-shortcut # Crée un lanceur automatique sur votre Bureau
    python3 launch.py --install                 # Force l'installation des dépendances Python/Node
    python3 launch.py --backend-only            # Lance uniquement l'API Python FastAPI sur le port 8000
    python3 launch.py --docker                  # Lance toute la stack via Docker Compose
"""

from __future__ import annotations

import argparse
import importlib.util
import os
import platform
import shutil
import signal
import subprocess
import sys
import time
import webbrowser
from pathlib import Path

ROOT_DIR = Path(__file__).resolve().parent
BACKEND_DIR = ROOT_DIR / "backend"
FRONTEND_DIR = ROOT_DIR / "frontend"


def load_env_file(env_path: Path, base_env: dict[str, str]) -> dict[str, str]:
    merged = dict(base_env)
    if not env_path.exists():
        return merged
    for raw_line in env_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip().strip('"').strip("'")
        if key and key not in merged:
            merged[key] = value
    return merged


def ensure_env_files() -> None:
    backend_env = BACKEND_DIR / ".env"
    backend_example = BACKEND_DIR / ".env.example"
    if not backend_env.exists() and backend_example.exists():
        shutil.copyfile(backend_example, backend_env)
        print(f"[Monty] Fichier créé : {backend_env} (copié depuis .env.example)")

    frontend_env = FRONTEND_DIR / ".env.local"
    frontend_example = FRONTEND_DIR / ".env.example"
    if not frontend_env.exists() and frontend_example.exists():
        shutil.copyfile(frontend_example, frontend_env)
        print(f"[Monty] Fichier créé : {frontend_env} (copié depuis .env.example)")


def install_dependencies(*, install_backend: bool = True, install_frontend: bool = True) -> None:
    if install_backend:
        print("[Monty] Installation des dépendances Python (backend/requirements.txt)...")
        subprocess.run(
            [sys.executable, "-m", "pip", "install", "-r", str(BACKEND_DIR / "requirements.txt")],
            cwd=str(BACKEND_DIR),
            check=True,
        )
    if install_frontend:
        npm_cmd = shutil.which("npm")
        if not npm_cmd:
            raise RuntimeError("npm est introuvable dans le PATH. Installez Node.js 20+.")
        print("[Monty] Installation des dépendances Node.js (frontend/package.json)...")
        subprocess.run([npm_cmd, "install"], cwd=str(FRONTEND_DIR), check=True)


def auto_install_if_missing(backend_only: bool) -> None:
    missing_python = (
        importlib.util.find_spec("fastapi") is None
        or importlib.util.find_spec("uvicorn") is None
    )
    missing_node = (not backend_only) and (not (FRONTEND_DIR / "node_modules").exists())
    if missing_python or missing_node:
        print("[Monty] Premier lancement détecté : installation automatique des dépendances manquantes...")
        install_dependencies(
            install_backend=missing_python,
            install_frontend=missing_node and shutil.which("npm") is not None,
        )


def resolve_desktop_dir() -> Path:
    home = Path.home()
    candidates = [
        home / "Desktop",
        home / "Bureau",
        home / "OneDrive" / "Desktop",
        home / "OneDrive" / "Bureau",
    ]
    for candidate in candidates:
        if candidate.is_dir():
            return candidate
    desktop = home / "Desktop"
    desktop.mkdir(parents=True, exist_ok=True)
    return desktop


def create_desktop_shortcut() -> int:
    desktop = resolve_desktop_dir()
    system = platform.system().lower()

    if system == "windows":
        target = desktop / "Lancer Monty Site.bat"
        content = (
            "@echo off\r\n"
            "title Monty Immobilier — Lanceur Automatique\r\n"
            f'cd /d "{ROOT_DIR}"\r\n'
            "where py >nul 2>nul && (py -3 launch.py --auto) || (python launch.py --auto)\r\n"
            "if errorlevel 1 pause\r\n"
        )
        target.write_text(content, encoding="utf-8")
    elif system == "darwin":
        target = desktop / "Lancer Monty Site.command"
        content = (
            "#!/bin/bash\n"
            f'cd "{ROOT_DIR}" || exit 1\n'
            'python3 launch.py --auto\n'
        )
        target.write_text(content, encoding="utf-8")
        target.chmod(0o755)
    else:
        target = desktop / "Lancer_Monty_Site.desktop"
        content = (
            "[Desktop Entry]\n"
            "Type=Application\n"
            "Name=Monty Immobilier (Site + SaaS)\n"
            "Comment=Lance automatiquement l'API Python et le Site Web Monty\n"
            f"Exec=python3 {ROOT_DIR / 'launch.py'} --auto\n"
            f"Path={ROOT_DIR}\n"
            "Terminal=true\n"
            "Categories=Office;Network;\n"
        )
        target.write_text(content, encoding="utf-8")
        target.chmod(0o755)

    print(f"[Monty] Lanceur créé sur votre Bureau : {target}")
    return 0


def run_docker_compose() -> int:
    docker_cmd = shutil.which("docker")
    if not docker_cmd:
        print("[Erreur] Docker n'est pas installé ou absent du PATH.", file=sys.stderr)
        return 1
    ensure_env_files()
    print("[Monty] Démarrage via Docker Compose (PostgreSQL + Redis + FastAPI + Worker + Next.js)...")
    return subprocess.call([docker_cmd, "compose", "up", "--build"], cwd=str(ROOT_DIR))


def main() -> int:
    parser = argparse.ArgumentParser(description="Lanceur Python Monty Immobilier")
    parser.add_argument("--backend-only", action="store_true", help="Lance uniquement l'API Python FastAPI")
    parser.add_argument("--install", action="store_true", help="Force l'installation pip & npm avant de démarrer")
    parser.add_argument("--auto", action="store_true", help="Mode bureau : installe si manquant + ouvre le navigateur")
    parser.add_argument("--create-desktop-shortcut", action="store_true", help="Crée un raccourci exécutable sur le Bureau")
    parser.add_argument("--docker", action="store_true", help="Lance la stack complète via docker compose")
    parser.add_argument("--port-api", type=int, default=8000, help="Port de l'API Python (défaut: 8000)")
    parser.add_argument("--port-web", type=int, default=3000, help="Port du Site Web (défaut: 3000)")
    parser.add_argument("--open-browser", action="store_true", help="Ouvre automatiquement le navigateur")
    args = parser.parse_args()

    if args.create_desktop_shortcut:
        return create_desktop_shortcut()

    if args.docker:
        return run_docker_compose()

    ensure_env_files()
    if args.install:
        install_dependencies(install_backend=True, install_frontend=not args.backend_only)
    elif args.auto:
        auto_install_if_missing(args.backend_only)

    open_browser = args.open_browser or args.auto

    backend_env = load_env_file(BACKEND_DIR / ".env", os.environ)
    backend_env.setdefault("ENVIRONMENT", "development")
    if backend_env.get("ENVIRONMENT", "development").lower() == "development":
        backend_env.setdefault("ENABLE_DEV_AUTH", "true")
    backend_env.setdefault("ALLOWED_ORIGINS", f"http://localhost:{args.port_web}")

    processes: list[subprocess.Popen] = []

    def shutdown(*_args: object) -> None:
        print("\n[Monty] Arrêt propre des serveurs...")
        for proc in processes:
            if proc.poll() is None:
                proc.terminate()
        for proc in processes:
            try:
                proc.wait(timeout=5)
            except subprocess.TimeoutExpired:
                proc.kill()
        sys.exit(0)

    signal.signal(signal.SIGINT, shutdown)
    signal.signal(signal.SIGTERM, shutdown)

    api_cmd = [
        sys.executable,
        "-m",
        "uvicorn",
        "app.main:app",
        "--host",
        "0.0.0.0",
        "--port",
        str(args.port_api),
        "--reload",
    ]
    print(f"[Monty] Démarrage API Python FastAPI sur http://localhost:{args.port_api} ...")
    api_proc = subprocess.Popen(api_cmd, cwd=str(BACKEND_DIR), env=backend_env)
    processes.append(api_proc)

    if not args.backend_only:
        npm_cmd = shutil.which("npm")
        if not npm_cmd:
            print(
                "[Avertissement] npm est introuvable : seul le backend Python a été démarré. "
                "Installez Node.js 20+ pour servir le frontend Next.js.",
                file=sys.stderr,
            )
        else:
            frontend_env = load_env_file(FRONTEND_DIR / ".env.local", os.environ)
            frontend_env.setdefault("NEXT_PUBLIC_API_BASE_URL", f"http://localhost:{args.port_api}")
            frontend_env["PORT"] = str(args.port_web)
            web_cmd = [npm_cmd, "run", "dev", "--", "--port", str(args.port_web)]
            print(f"[Monty] Démarrage Site Web + Cockpit SaaS sur http://localhost:{args.port_web} ...")
            web_proc = subprocess.Popen(web_cmd, cwd=str(FRONTEND_DIR), env=frontend_env)
            processes.append(web_proc)

    print("\n=== MONTY IMMOBILIER EN LIGNE (LOCAL) ===")
    if not args.backend_only:
        print(f"  Site Web Public  : http://localhost:{args.port_web}/site")
        print(f"  Annonces & Trajet: http://localhost:{args.port_web}/annonces")
        print(f"  Publier un bien  : http://localhost:{args.port_web}/publier")
        print(f"  Actualités       : http://localhost:{args.port_web}/actualites")
        print(f"  6 Simulateurs    : http://localhost:{args.port_web}/simulateurs")
        print(f"  Cockpit SaaS Pro : http://localhost:{args.port_web}/")
    print(f"  API FastAPI Docs : http://localhost:{args.port_api}/docs")
    print("  Appuyez sur Ctrl+C pour arrêter.\n")

    if open_browser:
        time.sleep(2.5)
        target = (
            f"http://localhost:{args.port_api}/docs"
            if args.backend_only
            else f"http://localhost:{args.port_web}/site"
        )
        webbrowser.open(target)

    while True:
        for proc in processes:
            code = proc.poll()
            if code is not None:
                print(f"[Monty] Un processus s'est arrêté (code {code}).")
                shutdown()
        time.sleep(0.8)


if __name__ == "__main__":
    raise SystemExit(main())
