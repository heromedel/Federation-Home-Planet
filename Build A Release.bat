@echo off
setlocal
cd /d "%~dp0"
title The Federation Home Planet Station - Release
rem Makes a release (6.51): builds the jar with the Construction Yard, then puts together a clean folder with only what
rem a player needs, and a zip of it to upload:
rem   Release\Federation Home Planet <version>\  the jar, the Interface (which finds Java, or offers to get it),
rem                                              README.txt (docs\README for players.txt), User-Manual.txt and .html, changes.txt, LICENSE.txt and CREDITS.txt
rem   Release\Federation Home Planet <version>.zip
rem The player's settings, fleets and logs are made beside the jar on first start: nothing of yours goes in.

set "VERSION="
rem the first <version> is the station's (the plugins' come after it)
for /f "tokens=2 delims=<>	 " %%v in ('findstr /c:"<version>" pom.xml 2^>nul') do if not defined VERSION set "VERSION=%%v"
if not defined VERSION ( echo pom.xml has no version: run this from the station's source folder. & goto :failed )
set "NAME=Federation Home Planet %VERSION%"
set "OUT=%~dp0Release\%NAME%"

echo ==========================================================
echo    THE FEDERATION HOME PLANET STATION
echo    Release %VERSION%
echo ==========================================================
echo.

rem git, in a clone (a zip download has none): on the PATH, or the copy that comes with GitHub Desktop
set "GIT="
if exist "%~dp0.git" (
	where git >nul 2>&1 && set "GIT=git"
	if not defined GIT for /d %%d in ("%LOCALAPPDATA%\GitHubDesktop\app-*") do if exist "%%d\resources\app\git\cmd\git.exe" set "GIT=%%d\resources\app\git\cmd\git.exe"
	if not defined GIT echo Note: git couldn't be found, so this release won't be tagged and uncommitted changes weren't checked.
)
if defined GIT (
	call "%GIT%" diff --quiet HEAD -- >nul 2>&1 || echo Warning: there are changes not yet committed: the release builds them too.
)
echo.

call "%~dp0Build The Federation Home Planet Station.bat" build
if errorlevel 1 ( echo. & echo The release wasn't made: the station couldn't be built. & goto :failed )
cd /d "%~dp0"

echo.
echo Putting the release together in "Release\%NAME%" ...
if exist "%OUT%" rmdir /s /q "%OUT%"
if exist "%OUT%.zip" del /f /q "%OUT%.zip"
mkdir "%OUT%"
for %%f in ("Current Build\Federation Home Planet.jar" "Current Build\Federation Home Planet Interface.bat") do (
	copy /y %%f "%OUT%\" >nul 2>&1
	if not exist "%OUT%\%%~nxf" ( echo %%~f couldn't be copied into the release. & goto :failed )
)
rem the player's text files open in Notepad: the players' readme, the license as it is, the credits without their Markdown marks
copy /y "docs\README for players.txt" "%OUT%\README.txt" >nul 2>&1
copy /y "LICENSE" "%OUT%\LICENSE.txt" >nul 2>&1
copy /y "User-Manual.txt" "%OUT%\User-Manual.txt" >nul 2>&1
copy /y "User-Manual.html" "%OUT%\User-Manual.html" >nul 2>&1
copy /y "changes.txt" "%OUT%\changes.txt" >nul 2>&1
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$t = Get-Content -Raw -Encoding UTF8 'CREDITS.md'; $t = $t -replace '(?m)^#+ ', '' -replace '\*\*', '' -replace '`', ''; Set-Content -Encoding UTF8 -Path '%OUT%\CREDITS.txt' -Value $t"
for %%f in (README.txt User-Manual.txt User-Manual.html changes.txt LICENSE.txt CREDITS.txt) do if not exist "%OUT%\%%f" ( echo %%f couldn't be put into the release. & goto :failed )
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ProgressPreference='SilentlyContinue'; Compress-Archive -Path '%OUT%' -DestinationPath '%OUT%.zip' -Force"
if not exist "%OUT%.zip" ( echo The zip couldn't be made. The folder is ready in "Release\%NAME%". & goto :failed )

if defined GIT call :tag
echo.
echo Release %VERSION% is ready:
echo    Release\%NAME%\
echo    Release\%NAME%.zip   (to upload)
echo.
pause
exit /b 0

:failed
echo.
pause
exit /b 1

rem tags this commit release-<version>, on this computer only (nothing is pushed): the code each release was made from
:tag
set "HEAD_ID=" & set "TAGGED="
for /f %%h in ('call "%GIT%" rev-parse HEAD 2^>nul') do set "HEAD_ID=%%h"
if not defined HEAD_ID exit /b 0
for /f %%t in ('call "%GIT%" rev-parse -q --verify "refs/tags/release-%VERSION%" 2^>nul') do set "TAGGED=%%t"
if "%TAGGED%"=="%HEAD_ID%" exit /b 0
call "%GIT%" tag -f "release-%VERSION%" >nul 2>&1 || ( echo Note: this commit couldn't be tagged release-%VERSION%. & exit /b 0 )
if defined TAGGED ( echo Tagged this commit release-%VERSION%, moved from an earlier commit: was the version bumped? ) else ( echo Tagged this commit release-%VERSION% ^(on this computer only^). )
exit /b 0
