@echo off
cd /d "%~dp0"
java -jar "HW to FHP Converter.jar"
if errorlevel 1 pause
