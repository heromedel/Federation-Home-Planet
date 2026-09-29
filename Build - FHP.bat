@echo off
setlocal
cd /d "%~dp0"
rem Builds Federation Home Planet from the source in this folder and puts the jar in "Current Build".
rem First run downloads a JDK and Maven into tools\ (about 250 MB, once); later runs are offline-capable.

set "TOOLS=%~dp0tools"
set "JDK=%TOOLS%\jdk"
set "MVN=%TOOLS%\maven"

if not exist "%JDK%\bin\javac.exe" (
    echo Getting a JDK into tools\jdk ...
    if not exist "%TOOLS%" mkdir "%TOOLS%"
    powershell -NoProfile -ExecutionPolicy Bypass -Command ^
      "$ProgressPreference='SilentlyContinue'; [Net.ServicePointManager]::SecurityProtocol='Tls12';" ^
      "Invoke-WebRequest 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk' -OutFile '%TOOLS%\jdk.zip';" ^
      "Expand-Archive '%TOOLS%\jdk.zip' '%TOOLS%\jdk-tmp' -Force;" ^
      "$d = Get-ChildItem '%TOOLS%\jdk-tmp' -Directory | Select-Object -First 1; Move-Item $d.FullName '%JDK%';" ^
      "Remove-Item '%TOOLS%\jdk-tmp' -Recurse -Force; Remove-Item '%TOOLS%\jdk.zip'"
    if not exist "%JDK%\bin\javac.exe" ( echo The JDK download failed. & pause & exit /b 1 )
)

if not exist "%MVN%\bin\mvn.cmd" (
    echo Getting Maven into tools\maven ...
    if not exist "%TOOLS%" mkdir "%TOOLS%"
    powershell -NoProfile -ExecutionPolicy Bypass -Command ^
      "$ProgressPreference='SilentlyContinue'; [Net.ServicePointManager]::SecurityProtocol='Tls12';" ^
      "Invoke-WebRequest 'https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip' -OutFile '%TOOLS%\maven.zip';" ^
      "Expand-Archive '%TOOLS%\maven.zip' '%TOOLS%\maven-tmp' -Force;" ^
      "$d = Get-ChildItem '%TOOLS%\maven-tmp' -Directory | Select-Object -First 1; Move-Item $d.FullName '%MVN%';" ^
      "Remove-Item '%TOOLS%\maven-tmp' -Recurse -Force; Remove-Item '%TOOLS%\maven.zip'"
    if not exist "%MVN%\bin\mvn.cmd" ( echo The Maven download failed. & pause & exit /b 1 )
)

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
