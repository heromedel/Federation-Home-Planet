@echo off
setlocal
rem Opens The Home Planet Station without a console window (javaw), so only the station itself shows in the taskbar.
rem The Java, the first found: java\ beside the jar (a release's own, below), the JDK the Construction Yard gathered (one
rem folder up, in tools\), JAVA_HOME, the PATH, then the usual install folders. With none, it offers to get one (6.51).
rem Only a missing Java keeps this window open. The station's own messages go in its log files (logs\).
cd /d "%~dp0"
set "HERE=%~dp0"
set "JRE_URL=https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jre/hotspot/normal/eclipse?project=jdk"
set "MSI_URL=https://api.adoptium.net/v3/installer/latest/17/ga/windows/x64/jre/hotspot/normal/eclipse?project=jdk"

call :findjava
if defined JAVAW goto :open
goto :nojava

:open
start "" "%JAVAW%" -jar "Federation Home Planet.jar" %*
exit /b 0

rem ---- :findjava: sets JAVAW to the first javaw.exe found, or leaves it empty ----
:findjava
set "JAVAW="
if exist "%HERE%java\bin\javaw.exe" ( set "JAVAW=%HERE%java\bin\javaw.exe" & exit /b 0 )
if exist "%HERE%..\tools\jdk\bin\javaw.exe" ( set "JAVAW=%HERE%..\tools\jdk\bin\javaw.exe" & exit /b 0 )
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javaw.exe" ( set "JAVAW=%JAVA_HOME%\bin\javaw.exe" & exit /b 0 )
for %%j in (javaw.exe) do set "JAVAW=%%~$PATH:j"
if defined JAVAW exit /b 0
for %%r in ("%ProgramFiles%" "%ProgramW6432%" "%ProgramFiles(x86)%") do (
	for %%v in ("Eclipse Adoptium" "Java" "Microsoft" "Zulu" "Amazon Corretto") do (
		for /f "delims=" %%d in ('dir /b /ad "%%~r\%%~v" 2^>nul') do if not defined JAVAW if exist "%%~r\%%~v\%%d\bin\javaw.exe" set "JAVAW=%%~r\%%~v\%%d\bin\javaw.exe"
	)
)
exit /b 0

rem ---- :nojava: no Java on this computer: get one, as the player chooses ----
:nojava
title Federation Home Planet Interface
cls
echo ==========================================================
echo    THE FEDERATION HOME PLANET STATION
echo    The connection could not be established: no Java was found.
echo ==========================================================
echo.
echo    The station runs on Java 8 or newer. It can get one for you:
echo.
echo    0: Cancel
echo    1: Download Java just for the station (about 45 MB)
echo       Unpacked into the "java" folder beside the station: no installer, no admin rights,
echo       nothing changed outside this folder. Delete the folder to remove it.
echo    2: Download and install Java on this computer (about 30 MB)
echo       The usual Windows install (Eclipse Temurin, from adoptium.net), for every program.
echo       Windows will ask for permission.
echo.
choice /c 012 /n /m "   Input -> "
if errorlevel 3 goto :install
if errorlevel 2 goto :local
exit /b 1

:local
echo.
call :fetch "Java" "%JRE_URL%" "%HERE%java-download.zip" 30000000 || goto :failed
echo Unpacking Java into "%HERE%java" ...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ProgressPreference='SilentlyContinue';" ^
  "Expand-Archive '%HERE%java-download.zip' '%HERE%java-download.tmp' -Force;" ^
  "$d = Get-ChildItem '%HERE%java-download.tmp' -Directory | Select-Object -First 1; Move-Item $d.FullName '%HERE%java';" ^
  "Remove-Item '%HERE%java-download.tmp' -Recurse -Force; Remove-Item '%HERE%java-download.zip'"
if not exist "%HERE%java\bin\javaw.exe" (
	echo Java could not be unpacked. Delete "java-download.zip" and the "java" folder if they're there, then run this again.
	goto :failed
)
echo Java is ready in the "java" folder. Establishing the connection ...
set "JAVAW=%HERE%java\bin\javaw.exe"
goto :open

:install
echo.
call :fetch "the Java installer" "%MSI_URL%" "%HERE%java-installer.msi" 20000000 || goto :failed
echo Starting the Java installer: follow its steps, then come back here.
rem the installer's own window; with Java on the PATH and JAVA_HOME set, so other programs find it too
start "" /wait msiexec /i "%HERE%java-installer.msi" ADDLOCAL=FeatureMain,FeatureEnvironment,FeatureJarFileRunWith,FeatureJavaHome
set "RESULT=%errorlevel%"
del /f /q "%HERE%java-installer.msi" >nul 2>&1
if not "%RESULT%"=="0" if not "%RESULT%"=="3010" (
	echo The Java install didn't finish ^(code %RESULT%^). Run this again to try once more, or choose 1 to keep Java beside the station instead.
	goto :failed
)
call :findjava
if not defined JAVAW (
	echo Java is installed, but this window can't see it yet. Run "Federation Home Planet Interface" again.
	goto :failed
)
echo Java is installed. Establishing the connection ...
goto :open

:failed
echo.
pause
exit /b 1

rem ---- :fetch name url file minbytes: curl (resumes a cut-off download on the next run), else PowerShell; checks the size ----
:fetch
echo Downloading %~1 ...
where curl.exe >nul 2>&1
if errorlevel 1 (
	powershell -NoProfile -ExecutionPolicy Bypass -Command ^
	  "$ProgressPreference='SilentlyContinue'; [Net.ServicePointManager]::SecurityProtocol='Tls12';" ^
	  "Invoke-WebRequest '%~2' -OutFile '%~3'"
) else (
	curl.exe -L --fail --retry 5 --retry-delay 3 -C - -o "%~3" "%~2"
)
if not exist "%~3" ( echo The %~1 download failed: nothing was saved. Check the connection and run this again. & exit /b 1 )
for %%s in ("%~3") do set "GOT=%%~zs"
if %GOT% LSS %~4 (
	echo The %~1 download stopped early ^(%GOT% bytes^). Run this again: it carries on where it left off.
	exit /b 1
)
exit /b 0
