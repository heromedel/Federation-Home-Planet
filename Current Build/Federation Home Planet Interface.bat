@echo off
rem Opens The Home Planet Station without a console window (javaw), so only the station itself shows in the taskbar.
rem The Java: the JDK the Construction Yard gathered (one folder up, in tools\), else Java on this computer.
rem Only a missing Java keeps this window open, to say so. The station's own messages go in its log files (logs\).
cd /d "%~dp0"
set "JAVAW=%~dp0..\tools\jdk\bin\javaw.exe"
if exist "%JAVAW%" goto :open
set "JAVAW="
for %%j in (javaw.exe) do set "JAVAW=%%~$PATH:j"
if not defined JAVAW goto :nojava
:open
start "" "%JAVAW%" -jar "Federation Home Planet.jar" %*
exit /b 0

:nojava
title Federation Home Planet Interface
echo The connection could not be established: no Java was found on this computer.
echo Install Java 8 or newer, or run "Build The Federation Home Planet Station.bat" once: it gathers its own.
echo.
pause
exit /b 1
