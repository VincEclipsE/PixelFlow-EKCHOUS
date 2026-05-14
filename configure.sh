#!/usr/bin/env bash
# POSIX configure for ekchous (macOS / Linux). Mirrors configure.bat.
#
# Picks Ninja if available, falls back to Unix Makefiles. CMake must be on
# PATH (`brew install cmake ninja` on macOS, `apt install cmake ninja-build`
# on Debian/Ubuntu).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "===================================="
echo "EKCHOUS Build Configuration"
echo "===================================="

if ! command -v cmake >/dev/null 2>&1; then
    echo "ERROR: cmake not found on PATH."
    echo "  macOS:  brew install cmake"
    echo "  Linux:  sudo apt install cmake"
    exit 1
fi

if command -v ninja >/dev/null 2>&1; then
    GENERATOR="Ninja"
else
    GENERATOR="Unix Makefiles"
    echo "Note: ninja not found, falling back to Unix Makefiles."
    echo "      Install with 'brew install ninja' for faster builds."
fi

BUILD_TYPE="${BUILD_TYPE:-Release}"

echo "Generator:    $GENERATOR"
echo "Build type:   $BUILD_TYPE"
echo

cmake -S "$SCRIPT_DIR" -B "$SCRIPT_DIR/build" \
    -G "$GENERATOR" \
    -DCMAKE_BUILD_TYPE="$BUILD_TYPE" \
    -DCMAKE_POLICY_VERSION_MINIMUM=3.5

echo
echo "Configuration complete! Run './build.sh' to compile."
