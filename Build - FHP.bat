@echo off
setlocal
cd /d "%~dp0"
rem Builds Federation Home Planet from the source in this folder and puts the jar in "Current Build".
rem First run downloads a JDK and Maven into tools\ (about 250 MB, once); later runs are offline-capable.

set "TOOLS=%~dp0tools"
set "JDK=%TOOLS%\jdk"
set "MVN=%TOOLS%\maven"

set "JDK_URL=https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk"
set "MVN_URL=https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip"

if not exist "%JDK%\bin\javac.exe" call :fetch "a JDK" "%JDK_URL%" "%TOOLS%\jdk.zip" "%JDK%" 100000000 || exit /b 1
if not exist "%MVN%\bin\mvn.cmd" call :fetch "Maven" "%MVN_URL%" "%TOOLS%\maven.zip" "%MVN%" 5000000 || exit /b 1
goto :build

rem ---- :fetch name url zip dest minbytes ----
rem Downloads with curl (resumes a cut-off download on the next run), checks the size, unpacks the zip's
rem single top folder as dest. Invoke-WebRequest is only the fallback: it holds the file in memory and
rem gives up on any hiccup.
:fetch
echo Getting %~1 into tools ...
if not exist "%TOOLS%" mkdir "%TOOLS%"
where curl.exe >nul 2>&1
if errorlevel 1 (
    powershell -NoProfile -ExecutionPolicy Bypass -Command ^
      "$ProgressPreference='SilentlyContinue'; [Net.ServicePointManager]::SecurityProtocol='Tls12';" ^
      "Invoke-WebRequest '%~2' -OutFile '%~3'"
) else (
    curl.exe -L --fail --retry 5 --retry-delay 3 -C - -o "%~3" "%~2"
)
if not exist "%~3" ( echo The %~1 download failed: nothing was saved. Check the connection and run this again. & pause & exit /b 1 )
for %%s in ("%~3") do set "GOT=%%~zs"
if %GOT% LSS %~5 (
    echo The %~1 download stopped early ^(%GOT% bytes^). Run this again: it carries on where it left off.
    pause
    exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ProgressPreference='SilentlyContinue';" ^
  "Expand-Archive '%~3' '%~3.tmp' -Force;" ^
  "$d = Get-ChildItem '%~3.tmp' -Directory | Select-Object -First 1; Move-Item $d.FullName '%~4';" ^
  "Remove-Item '%~3.tmp' -Recurse -Force; Remove-Item '%~3'"
if not exist "%~4" (
    echo The %~1 zip could not be unpacked. Delete "%~3" and run this again.
    pause
    exit /b 1
)
exit /b 0

:build
set "JAVA_HOME=%JDK%"
set "PATH=%JDK%\bin;%MVN%\bin;%PATH%"

echo Building ...
if exist target rmdir /s /q target
call "%MVN%\bin\mvn.cmd" -q package -DskipTests
if errorlevel 1 ( echo Build failed - see the messages above. & pause & exit /b 1 )

if not exist "Current Build" mkdir "Current Build"
copy /y "target\Federation Home Planet.jar" "Current Build\Federation Home Planet.jar" >nul
echo.
echo Done: "Current Build\Federation Home Planet.jar"
for /f "tokens=2 delims=<>" %%v in ('findstr /c:"<version>" pom.xml') do ( echo Version %%v & goto :shown )
:shown
pause
