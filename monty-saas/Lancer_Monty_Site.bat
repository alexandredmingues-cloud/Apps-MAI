@echo off
chcp 65001 >nul
title Monty Immobilier — Lanceur Automatique

set "PROJECT_DIR=%~dp0"
if not exist "%PROJECT_DIR%launch.py" (
    if exist "%USERPROFILE%\Desktop\monty-saas\launch.py" set "PROJECT_DIR=%USERPROFILE%\Desktop\monty-saas\"
    if exist "%USERPROFILE%\Bureau\monty-saas\launch.py" set "PROJECT_DIR=%USERPROFILE%\Bureau\monty-saas\"
    if exist "%USERPROFILE%\Documents\monty-saas\launch.py" set "PROJECT_DIR=%USERPROFILE%\Documents\monty-saas\"
    if exist "%USERPROFILE%\monty-saas\launch.py" set "PROJECT_DIR=%USERPROFILE%\monty-saas\"
)

if not exist "%PROJECT_DIR%launch.py" (
    echo [Erreur] Dossier monty-saas introuvable.
    echo Placez un raccourci vers ce fichier depuis le dossier monty-saas.
    pause
    exit /b 1
)

cd /d "%PROJECT_DIR%"
where py >nul 2>nul
if %errorlevel%==0 (
    py -3 launch.py --auto
) else (
    python launch.py --auto
)

if errorlevel 1 (
    echo.
    echo [Monty] Une erreur est survenue lors du demarrage.
    pause
)
