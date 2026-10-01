@echo off
setlocal
cd /d "%~dp0"
rem the folder, kept for the subroutines (inside a CALLed label, %0 is the label)
set "HERE=%~dp0"
title The Federation Home Planet Station - Construction Yard
rem Builds Federation Home Planet from the source in this folder and puts the jar in "Current Build".
rem First run downloads a JDK and Maven into tools\ (about 250 MB, once); later runs are offline-capable.

set "TOOLS=%~dp0tools"
set "JDK=%TOOLS%\jdk"
set "MVN=%TOOLS%\maven"

set "JDK_URL=https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk"
set "MVN_URL=https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip"

:menu
cls
echo ==========================================================
echo    THE FEDERATION HOME PLANET STATION
echo    Construction Yard
echo ==========================================================
echo.
echo    0: Exit
echo    1: Construct Federation Home Planet Station
echo    2: Construct Federation Home Planet Station with Quick Link
echo       (adds a Quick Link to the Station Interface on your desktop)
echo.
choice /c 012 /n /m "   Input -> "
if errorlevel 3 ( call :construct && call :quicklink & goto :after )
if errorlevel 2 ( call :construct & goto :after )
exit /b 0

:after
echo.
pause
exit /b 0

rem ---- :construct: tools if missing, then the build ----
:construct
echo.
if not exist "%JDK%\bin\javac.exe" call :fetch "a JDK" "%JDK_URL%" "%TOOLS%\jdk.zip" "%JDK%" 100000000 || exit /b 1
if not exist "%MVN%\bin\mvn.cmd" call :fetch "Maven" "%MVN_URL%" "%TOOLS%\maven.zip" "%MVN%" 5000000 || exit /b 1
set "JAVA_HOME=%JDK%"
set "PATH=%JDK%\bin;%MVN%\bin;%PATH%"

echo Constructing Station...
if exist target rmdir /s /q target
call "%MVN%\bin\mvn.cmd" -q package -DskipTests
if errorlevel 1 ( echo. & echo Station construction failed: see the messages above. & exit /b 1 )

if not exist "Current Build" mkdir "Current Build"
copy /y "target\Federation Home Planet.jar" "Current Build\Federation Home Planet.jar" >nul
if errorlevel 1 ( echo. & echo The new station could not be moved into "Current Build". Close Federation Home Planet if it's running, then construct again. & exit /b 1 )
echo.
echo Station Constructed and Ready in Current Build.
for /f "tokens=2 delims=<>	 " %%v in ('findstr /c:"<version>" pom.xml') do ( echo Version: %%v & goto :shown )
:shown
echo To Establish Connection, run Federation Home Planet Interface.
echo.
echo The rebellion won't stand a chance...
exit /b 0

rem ---- :quicklink: a desktop shortcut to the Interface, with the station's icon ----
:quicklink
echo.
echo Adding a Quick Link to the Station Interface on your desktop ...
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ico = '%HERE%Current Build\Federation Home Planet.ico';" ^
  "Copy-Item -Force '%HERE%src\main\resources\homeplanet\resource\icon\FederationHomePlanet.ico' $ico;" ^
  "$s = (New-Object -ComObject WScript.Shell).CreateShortcut([Environment]::GetFolderPath('Desktop') + '\Federation Home Planet Interface.lnk');" ^
  "$s.TargetPath = '%HERE%Current Build\Federation Home Planet Interface.bat'; $s.WorkingDirectory = '%HERE%Current Build';" ^
  "$s.IconLocation = $ico; $s.Description = 'Establish a connection with The Home Planet Station'; $s.Save()"
if errorlevel 1 ( echo The Quick Link could not be made: see the message above. The station itself is ready in "Current Build". & exit /b 1 )
echo Quick Link ready: "Federation Home Planet Interface" on your desktop.
exit /b 0

rem ---- :fetch name url zip dest minbytes ----
rem Downloads with curl (resumes a cut-off download on the next run), checks the size, unpacks the zip's
rem single top folder as dest. Invoke-WebRequest is only the fallback: it holds the file in memory and
rem gives up on any hiccup.
:fetch
echo Gathering construction materials (%~1) ...
if not exist "%TOOLS%" mkdir "%TOOLS%"
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
if %GOT% LSS %~5 (
    echo The %~1 download stopped early ^(%GOT% bytes^). Run this again: it carries on where it left off.
    exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ProgressPreference='SilentlyContinue';" ^
  "Expand-Archive '%~3' '%~3.tmp' -Force;" ^
  "$d = Get-ChildItem '%~3.tmp' -Directory | Select-Object -First 1; Move-Item $d.FullName '%~4';" ^
  "Remove-Item '%~3.tmp' -Recurse -Force; Remove-Item '%~3'"
if not exist "%~4" (
    echo The %~1 zip could not be unpacked. Delete "%~3" and run this again.
    exit /b 1
)
exit /b 0

