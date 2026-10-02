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
set "BUILT="
rem the words for a build; an update rebuilds, so it says so
set "DOING=Constructing"
set "DONE=Constructed"

rem "update": started by the station's Check for Updates, with the new files in place: rebuild, then open the station
if /i "%~1"=="update" goto :update

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
if not defined BUILT ( pause & exit /b 0 )
echo    0: Exit
echo    1: Launch the Station Interface
echo.
choice /c 01 /n /m "   Input -> "
if errorlevel 2 call :launch
exit /b 0

rem ---- :update: the station closed itself for new construction plans ----
:update
set "DOING=Reconstructing"
set "DONE=Reconstructed"
cls
echo ==========================================================
echo    THE FEDERATION HOME PLANET STATION
echo    Construction Yard: new construction plans
echo ==========================================================
call :construct
if defined BUILT ( call :launch & exit /b 0 )
call :restore
echo.
pause
exit /b 1

rem ---- :restore: puts back the files the update replaced, and takes away the ones it added ----
:restore
if not exist "update-backup" exit /b 0
echo.
echo Putting the old construction plans back ...
if exist "update-backup\added.txt" for /f "usebackq delims=" %%f in ("update-backup\added.txt") do if exist "%%f" del /f /q "%%f"
if exist "update-backup\files" xcopy "update-backup\files" "." /e /i /y /q >nul
echo The station was not changed: the version you had is still in "Current Build".
echo Run this again later, or download the program from https://github.com/heromedel/Federation-Home-Planet
exit /b 0

rem ---- :launch: opens the Station Interface in its own window ----
:launch
start "Federation Home Planet Interface" /d "%HERE%Current Build" "%HERE%Current Build\Federation Home Planet Interface.bat"
exit /b 0

rem ---- :construct: tools if missing, then the build ----
:construct
echo.
rem the first construction gathers its tools: say so before the long wait
set "FIRST="
if not exist "%JDK%\bin\javac.exe" set "FIRST=1"
if not exist "%MVN%\bin\mvn.cmd" set "FIRST=1"
if defined FIRST call :firstnote
if not exist "%JDK%\bin\javac.exe" call :fetch "a JDK" "%JDK_URL%" "%TOOLS%\jdk.zip" "%JDK%" 100000000 || exit /b 1
if not exist "%MVN%\bin\mvn.cmd" call :fetch "Maven" "%MVN_URL%" "%TOOLS%\maven.zip" "%MVN%" 5000000 || exit /b 1
set "JAVA_HOME=%JDK%"
set "PATH=%JDK%\bin;%MVN%\bin;%PATH%"

echo %DOING% Station...
if exist target rmdir /s /q target
rem Maven's messages go to a log first, so a refused certificate can be explained after them
call "%MVN%\bin\mvn.cmd" -q package -DskipTests > "%TOOLS%\last-build.log" 2>&1
set "MVNRESULT=%errorlevel%"
type "%TOOLS%\last-build.log"
if "%MVNRESULT%"=="0" goto :built
findstr /i /c:"PKIX" /c:"CertPath" /c:"bad_certificate" "%TOOLS%\last-build.log" >nul
if not errorlevel 1 call :certhint
echo.
echo Station construction failed: see the messages above.
exit /b 1
:built

if not exist "Current Build" mkdir "Current Build"
rem a station that has just closed may hold its jar for a moment: try for about half a minute
set /a TRIES=0
:copyjar
copy /y "target\Federation Home Planet.jar" "Current Build\Federation Home Planet.jar" >nul 2>&1
if not errorlevel 1 goto :copied
set /a TRIES+=1
if %TRIES% GEQ 15 ( echo. & echo The new station could not be moved into "Current Build". Close Federation Home Planet if it's running, then construct again. & exit /b 1 )
timeout /t 2 /nobreak >nul
goto :copyjar
:copied
echo.
echo Station %DONE% and Ready in Current Build.
for /f "tokens=2 delims=<>	 " %%v in ('findstr /c:"<version>" pom.xml') do ( echo Version: %%v & goto :shown )
:shown
echo To Establish Connection, run Federation Home Planet Interface.
echo.
echo The rebellion won't stand a chance...
set "BUILT=1"
exit /b 0

rem ---- :firstnote: the first construction is the slow one ----
:firstnote
echo ==========================================================
echo  FIRST CONSTRUCTION: this one takes a while.
echo.
echo  The Construction Yard is gathering its tools: a JDK and Maven
echo  (about 250 MB), then Maven's own parts. They're kept in tools\,
echo  so every construction after this one is much faster.
echo.
echo  Leave this window open until it says the station is ready.
echo ==========================================================
echo.
exit /b 0

rem ---- :certhint: Maven couldn't check a website's security certificate ----
:certhint
echo.
echo ==========================================================
echo  The construction materials couldn't be downloaded: Java could not check the
echo  security certificate of Maven's website. This is almost always one of these:
echo.
echo  1. This computer's date or time is wrong. In Windows Settings, open Time and
echo     language, then Date and time: turn on "Set time automatically" and press
echo     "Sync now". Then run this again.
echo  2. An antivirus that scans secure (HTTPS) connections, such as Avast, AVG,
echo     Kaspersky or ESET. Pause its web or HTTPS scanning for a moment, or add an
echo     exception, then run this again.
echo ==========================================================
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

