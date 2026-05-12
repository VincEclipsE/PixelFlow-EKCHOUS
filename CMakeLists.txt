# EKCHOUS Pixel Physics Engine
# C++/OpenGL implementation of the EKCHOUS pixel physics simulation.
# See docs/architecture.md for the design.

cmake_minimum_required(VERSION 3.24)
project(ekchous
    VERSION 0.0.1
    DESCRIPTION "EKCHOUS Pixel Physics Engine (Day-One skeleton)"
    LANGUAGES CXX
)

# C++20 everywhere, no extensions
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Build options
option(EKCHOUS_BUILD_TESTS "Build unit and replay tests" ON)
option(EKCHOUS_LINT_SHADERS "Run banned-ops lint on .comp shaders at build time" ON)
option(EKCHOUS_ENABLE_TRACY "Enable Tracy profiler integration (optional)" OFF)

# Warnings
if(MSVC)
    add_compile_options(/W4 /permissive- /Zc:__cplusplus)
else()
    add_compile_options(-Wall -Wextra -Wpedantic -Wno-unused-parameter)
endif()

# Determinism: disable subnormals (flush-to-zero) at compile time where the compiler supports it.
# This is a defense-in-depth; runtime asserts also check.
if(MSVC)
    add_compile_options(/fp:strict)
else()
    add_compile_options(-ffp-contract=off)
    # NOTE: do NOT use -ffast-math; it breaks IEEE-754 conformance.
endif()

# Dependencies
include(${CMAKE_SOURCE_DIR}/cmake/deps.cmake)

# Asset directory paths baked into the binary as a compile-time string.
set(EKCHOUS_ASSET_DIR "${CMAKE_SOURCE_DIR}/assets")
set(EKCHOUS_SHADER_DIR "${CMAKE_SOURCE_DIR}/shaders/glsl")

add_compile_definitions(
    EKCHOUS_ASSET_DIR=\"${EKCHOUS_ASSET_DIR}\"
    EKCHOUS_SHADER_DIR=\"${EKCHOUS_SHADER_DIR}\"
)

# ----- engine library -----
add_library(ekchous_engine STATIC
    engine/core/Logger.cpp
    engine/determinism/GoldenHash.cpp
    engine/determinism/SeededRng.cpp
    engine/sphere/CubedSphere.cpp
    engine/gpu/Shader.cpp
    engine/gpu/Buffer.cpp
    engine/gpu/Texture.cpp
    engine/engine/Engine.cpp
    engine/sim/grid/Chunk.cpp
    engine/sim/passes/FallingSandDemo.cpp
    engine/sim/passes/StubPasses.cpp
)
target_include_directories(ekchous_engine PUBLIC ${CMAKE_SOURCE_DIR})
target_link_libraries(ekchous_engine PUBLIC
    glfw
    glad
    glm::glm
    imgui
    spdlog::spdlog
    xxHash::xxhash
    nlohmann_json::nlohmann_json
)

if(EKCHOUS_ENABLE_TRACY)
    target_link_libraries(ekchous_engine PUBLIC Tracy::TracyClient)
    target_compile_definitions(ekchous_engine PUBLIC EKCHOUS_TRACY=1)
endif()

# ----- main executable -----
add_executable(ekchous engine/main.cpp)
target_link_libraries(ekchous PRIVATE ekchous_engine)

# ----- offline LUT generator (build-time tool) -----
# Runs once at build time to generate assets/sphere/face_adjacency.bin
add_executable(face_adjacency_gen engine/sphere/FaceAdjacencyGen.cpp)
target_link_libraries(face_adjacency_gen PRIVATE ekchous_engine)

add_custom_command(
    OUTPUT ${CMAKE_SOURCE_DIR}/assets/sphere/face_adjacency.bin
    COMMAND face_adjacency_gen ${CMAKE_SOURCE_DIR}/assets/sphere/face_adjacency.bin
    DEPENDS face_adjacency_gen
    COMMENT "Generating cubed-sphere face-adjacency LUT"
)
add_custom_target(face_adjacency_lut ALL DEPENDS ${CMAKE_SOURCE_DIR}/assets/sphere/face_adjacency.bin)
add_dependencies(ekchous face_adjacency_lut)

# ----- banned-ops shader lint (build-time check) -----
if(EKCHOUS_LINT_SHADERS)
    add_executable(banned_ops_lint engine/determinism/BannedOpsLint.cpp)
    target_include_directories(banned_ops_lint PRIVATE ${CMAKE_SOURCE_DIR})

    add_custom_command(
        OUTPUT ${CMAKE_BINARY_DIR}/banned_ops_lint.stamp
        COMMAND banned_ops_lint ${CMAKE_SOURCE_DIR}/shaders/glsl/sim
        COMMAND ${CMAKE_COMMAND} -E touch ${CMAKE_BINARY_DIR}/banned_ops_lint.stamp
        DEPENDS banned_ops_lint
        COMMENT "Linting compute shaders for banned ops (fma, sin/cos/exp/log/pow, imageAtomicCAS)"
    )
    add_custom_target(shader_lint ALL DEPENDS ${CMAKE_BINARY_DIR}/banned_ops_lint.stamp)
    add_dependencies(ekchous shader_lint)
endif()

# ----- tests -----
if(EKCHOUS_BUILD_TESTS)
    enable_testing()
    add_subdirectory(tests)
endif()

message(STATUS "")
message(STATUS "EKCHOUS configured:")
message(STATUS "  C++ standard:   ${CMAKE_CXX_STANDARD}")
message(STATUS "  Build type:     ${CMAKE_BUILD_TYPE}")
message(STATUS "  Build tests:    ${EKCHOUS_BUILD_TESTS}")
message(STATUS "  Lint shaders:   ${EKCHOUS_LINT_SHADERS}")
message(STATUS "  Tracy:          ${EKCHOUS_ENABLE_TRACY}")
message(STATUS "")
