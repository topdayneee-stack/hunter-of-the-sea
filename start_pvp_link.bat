@echo off
title Hunter of the Sea - Khoi chay Server & Mo Public Link PvP
color 0b
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start_server_and_tunnel.ps1"
