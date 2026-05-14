# PixelFlow Softbody — C++ port (standalone base)
# A clean port of Thomas Diewald's PixelFlow softbody dynamics:
#   - DwParticle2D (Verlet particle)
#   - DwSpringConstraint2D (distance constraint)
#   - DwCollisionGrid (PPLL spatial hash)
#   - DwPhysics (orchestrator)
# Plus a minimal interactive demo (drop balls, drag particles).

cmake_minimum_required(VERSION 3.24)
project(ekchous
    VERSION 0.0.1
    DESCRIPTION "PixelFlow softbody C++ port"
    LANGUAGES CXX C
)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

option(EKCHOUS_ENABLE_TRACY "Enable Tracy profiler integration (optional)" OFF)

if(MSVC)
    add_compile_options(/W4 /permissive- /Zc:__cplusplus)
else()
    add_compile_options(-Wall -Wextra -Wpedantic -Wno-unused-parameter)
endif()

# Dependencies (glfw, glad, glm, imgui, spdlog, etc).
include(${CMAKE_SOURCE_DIR}/cmake/deps.cmake)

# ----- engine library -----
add_library(ekchous_engine STATIC
    engine/core/Logger.cpp
    engine/gpu/Shader.cpp
    engine/gpu/Buffer.cpp
    engine/gpu/Texture.cpp
    engine/engine/Engine.cpp
    engine/sim/softbody/CollisionGrid.cpp
    engine/sim/softbody/Fluid2D.cpp
    engine/sim/softbody/Physics.cpp
)
target_include_directories(ekchous_engine PUBLIC ${CMAKE_SOURCE_DIR})
target_compile_definitions(ekchous_engine PUBLIC GLFW_INCLUDE_NONE)
target_link_libraries(ekchous_engine PUBLIC
    glfw
    glad
    glm::glm
    imgui
    spdlog::spdlog
)

if(EKCHOUS_ENABLE_TRACY)
    target_link_libraries(ekchous_engine PUBLIC Tracy::TracyClient)
    target_compile_definitions(ekchous_engine PUBLIC EKCHOUS_TRACY=1)
endif()

# ----- main executable -----
add_executable(ekchous engine/main.cpp)
target_link_libraries(ekchous PRIVATE ekchous_engine)

message(STATUS "")
message(STATUS "PixelFlow softbody C++ port configured:")
message(STATUS "  C++ standard:   ${CMAKE_CXX_STANDARD}")
message(STATUS "  Build type:     ${CMAKE_BUILD_TYPE}")
message(STATUS "  Tracy:          ${EKCHOUS_ENABLE_TRACY}")
message(STATUS "")
