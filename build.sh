#!/usr/bin/env bash
# POSIX build for ekchous (macOS / Linux). Mirrors build.bat.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "===================================="
echo "EKCHOUS Build"
echo "===================================="

if [ ! -d "build" ]; then
    echo "Build directory not found. Running configure first..."
    "$SCRIPT_DIR/configure.sh"
fi

# Use all CPU cores. nproc on Linux, sysctl on macOS.
if command -v nproc >/dev/null 2>&1; then
    JOBS="$(nproc)"
elif command -v sysctl >/dev/null 2>&1; then
    JOBS="$(sysctl -n hw.ncpu)"
else
    JOBS=4
fi

cmake --build "$SCRIPT_DIR/build" -j "$JOBS"

echo
echo "===================================="
echo "Build successful!"
echo "Run: ./build/ekchous"
echo "===================================="
