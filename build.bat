@echo off
echo ====================================
echo EKCHOUS Build
echo ====================================
cd /d D:\PixelFlow-EKCHOUS

if not exist "build" (
    echo Build directory not found. Running configure first...
    call configure.bat
    if errorlevel 1 exit /b 1
)

REM Resolve VS install via vswhere (matches configure.bat)
set "VSWHERE=C:\Program Files (x86)\Microsoft Visual Studio\Installer\vswhere.exe"
if not exist "%VSWHERE%" (
    echo ERROR: vswhere.exe not found.
    exit /b 1
)
for /f "usebackq tokens=*" %%i in (`"%VSWHERE%" -latest -prerelease -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath`) do set "VSINSTALL=%%i"
if not defined VSINSTALL (
    echo ERROR: No Visual Studio install with the C++ workload was found.
    exit /b 1
)
call "%VSINSTALL%\VC\Auxiliary\Build\vcvars64.bat" > nul

"C:\Program Files\CMake\bin\cmake.exe" --build build

if errorlevel 1 (
    echo.
    echo Build failed!
    exit /b 1
) else (
    echo.
    echo ====================================
    echo Build successful!
    echo Run: build\ekchous.exe
    echo ====================================
)
