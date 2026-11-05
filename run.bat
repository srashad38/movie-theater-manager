@echo off
cd /d "%~dp0"
java -jar movie-theater-manager.jar %*
if errorlevel 1 pause
