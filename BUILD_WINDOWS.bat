@echo off
setlocal
cd /d "%~dp0"
echo === FarSight LOD build ===
where java >nul 2>&1
if errorlevel 1 (
  echo ERROR: Java not found. Install JDK 25 and make sure java works in a NEW cmd window.
  pause
  exit /b 1
)
for /f "tokens=3" %%V in ('java -version 2^>^&1 ^| findstr /i "version"') do set "JAVA_VERSION=%%~V"
echo Detected Java: %JAVA_VERSION%
where gradle >nul 2>&1
if not errorlevel 1 goto gradle_on_path

echo Gradle is installed but is not on PATH.
echo Open the Gradle bin folder and copy the FULL path to gradle.bat.
set /p "GRADLE_FULL=Paste full path to gradle.bat (without quotes): "
if not exist "%GRADLE_FULL%" (
  echo ERROR: File not found: %GRADLE_FULL%
  pause
  exit /b 1
)
echo Running Gradle build...
call "%GRADLE_FULL%" build
goto build_result

:gradle_on_path
echo Running Gradle build...
gradle build

:build_result
if errorlevel 1 (
  echo.
  echo BUILD FAILED. Send me the full error text or a screenshot.
  pause
  exit /b 1
)
echo.
echo BUILD SUCCESS. JAR files are in build\libs\
pause
