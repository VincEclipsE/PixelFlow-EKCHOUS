@echo off
echo ====================================
echo EKCHOUS Build Configuration
echo ====================================
cd /d D:\PixelFlow-EKCHOUS

REM Resolve VS install via vswhere (handles 2022 / 2026 / future versions)
set "VSWHERE=C:\Program Files (x86)\Microsoft Visual Studio\Installer\vswhere.exe"
if not exist "%VSWHERE%" (
    echo ERROR: vswhere.exe not found. Install Visual Studio Installer.
    exit /b 1
)

for /f "usebackq tokens=*" %%i in (`"%VSWHERE%" -latest -prerelease -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath`) do set "VSINSTALL=%%i"

if not defined VSINSTALL (
    echo ERROR: No Visual Studio install with the C++ workload was found.
    exit /b 1
)

if not exist "%VSINSTALL%\VC\Auxiliary\Build\vcvars64.bat" (
    echo ERROR: vcvars64.bat missing at %VSINSTALL%\VC\Auxiliary\Build\
    exit /b 1
)

echo Using Visual Studio at: %VSINSTALL%
call "%VSINSTALL%\VC\Auxiliary\Build\vcvars64.bat" > nul
"C:\Program Files\CMake\bin\cmake.exe" -B build -G "NMake Makefiles" -DCMAKE_POLICY_VERSION_MINIMUM=3.5

echo.
echo Configuration complete! Run 'build.bat' to compile.
