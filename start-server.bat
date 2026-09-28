@echo off
REM Open-Voxel debug server (online mode, port 25565, auth via localhost:8370)
REM Offline/insecure mode: remove --online-mode and --auth-server
java -jar "%~dp0server\target\openvoxel-server-0.0.7-Debug.jar" --port 25565 --seed 2025 --online-mode true --auth-server http://localhost:8370