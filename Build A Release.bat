@echo off
setlocal
cd /d "%~dp0"
title The Federation Home Planet Station - Release
rem Makes a release (6.51): builds the jar with the Construction Yard, then puts together a clean folder with only what
rem a player needs, and a zip of it to upload:
rem   Release\Federation Home Planet <version>\  the jar, the Interface (which finds Java, or offers to get it),
rem                                              LICENSE, CREDITS.md and README.md
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

rem the version should be a tagged, committed one; a test release carries on after the warning
where git >nul 2>&1
if errorlevel 1 (
	echo Note: git isn't on this computer's PATH, so the tag and uncommitted changes weren't checked.
) else (
	git describe --exact-match --tags >nul 2>&1 || echo Warning: this commit has no tag. Tag it %VERSION% for a real release.
	for /f %%c in ('git status --porcelain --untracked-files=no 2^>nul ^| find /c /v ""') do if not "%%c"=="0" echo Warning: there are changes not yet committed: the release builds them too.
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
for %%f in ("Current Build\Federation Home Planet.jar" "Current Build\Federation Home Planet Interface.bat" "LICENSE" "CREDITS.md" "README.md") do (
	copy /y %%f "%OUT%\" >nul 2>&1
	if not exist "%OUT%\%%~nxf" ( echo %%~f couldn't be copied into the release. & goto :failed )
)
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ProgressPreference='SilentlyContinue'; Compress-Archive -Path '%OUT%' -DestinationPath '%OUT%.zip' -Force"
if not exist "%OUT%.zip" ( echo The zip couldn't be made. The folder is ready in "Release\%NAME%". & goto :failed )

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
