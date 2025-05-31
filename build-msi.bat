@echo off
echo ===============================================
echo    Building Voice Call App MSI Installer
echo ===============================================
echo.

echo Building MSI installer...
echo.

REM Build the MSI using Gradle
call gradlew.bat buildMsi

if errorlevel 1 (
    echo.
    echo ❌ Build failed
    pause
    exit /b 1
)

echo.
echo ✅ Build completed successfully!
echo.
echo The MSI installer can be found in:
echo   build\compose\binaries\main\msi\
echo.
echo You can now install the application using the MSI file.
echo.
pause
