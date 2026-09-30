@echo off
title Federation Home Planet Interface
cd /d "%~dp0"
echo Establishing connection with The Home Planet Station...
java -jar "Federation Home Planet.jar"
if errorlevel 1 (
    echo.
    echo The connection was lost: Federation Home Planet stopped with an error ^(see the messages above^).
    echo If Windows says 'java' is not recognized, install Java 8 or newer and try again.
    pause
    exit /b 1
)
echo Connection closed. The Home Planet Station stands by.
timeout /t 3 >nul
