# Dependencies for EKCHOUS engine.
# All fetched via FetchContent; no system installs required.
# Versions pinned to specific git tags to ensure reproducibility.

include(FetchContent)

set(FETCHCONTENT_QUIET FALSE)

# ---- GLFW ----
FetchContent_Declare(
    glfw
    GIT_REPOSITORY https://github.com/glfw/glfw.git
    GIT_TAG        3.4
    GIT_SHALLOW    TRUE
)
set(GLFW_BUILD_DOCS OFF CACHE BOOL "")
set(GLFW_BUILD_TESTS OFF CACHE BOOL "")
set(GLFW_BUILD_EXAMPLES OFF CACHE BOOL "")
set(GLFW_INSTALL OFF CACHE BOOL "")
FetchContent_MakeAvailable(glfw)

# ---- glad2 ----
# glad2 isn't a single git repo; we use the official prebuilt loader.
# For Day-One, we vendor a minimal GL 4.3 core glad implementation here.
# (In a real project, you'd generate via https://glad.dav1d.de/ and commit.)
add_library(glad STATIC IMPORTED GLOBAL)
if(NOT EXISTS "${CMAKE_SOURCE_DIR}/third_party/glad/include/glad/gl.h")
    message(WARNING "glad files not vendored yet. See third_party/glad/README.md to generate them.")
    # For now, expose an INTERFACE library so the rest of the build doesn't fail.
    add_library(glad INTERFACE)
endif()

# ---- glm (header-only math, render-side ONLY) ----
FetchContent_Declare(
    glm
    GIT_REPOSITORY https://github.com/g-truc/glm.git
    GIT_TAG        1.0.1
    GIT_SHALLOW    TRUE
)
set(GLM_BUILD_LIBRARY OFF CACHE BOOL "")
FetchContent_MakeAvailable(glm)

# ---- Dear ImGui ----
FetchContent_Declare(
    imgui
    GIT_REPOSITORY https://github.com/ocornut/imgui.git
    GIT_TAG        v1.91.5
    GIT_SHALLOW    TRUE
)
FetchContent_MakeAvailable(imgui)

add_library(imgui STATIC
    ${imgui_SOURCE_DIR}/imgui.cpp
    ${imgui_SOURCE_DIR}/imgui_draw.cpp
    ${imgui_SOURCE_DIR}/imgui_tables.cpp
    ${imgui_SOURCE_DIR}/imgui_widgets.cpp
    ${imgui_SOURCE_DIR}/imgui_demo.cpp
    ${imgui_SOURCE_DIR}/backends/imgui_impl_glfw.cpp
    ${imgui_SOURCE_DIR}/backends/imgui_impl_opengl3.cpp
)
target_include_directories(imgui PUBLIC
    ${imgui_SOURCE_DIR}
    ${imgui_SOURCE_DIR}/backends
)
target_link_libraries(imgui PUBLIC glfw)

# ---- spdlog ----
FetchContent_Declare(
    spdlog
    GIT_REPOSITORY https://github.com/gabime/spdlog.git
    GIT_TAG        v1.14.1
    GIT_SHALLOW    TRUE
)
set(SPDLOG_INSTALL OFF CACHE BOOL "")
FetchContent_MakeAvailable(spdlog)

# ---- xxHash ----
FetchContent_Declare(
    xxhash
    GIT_REPOSITORY https://github.com/Cyan4973/xxHash.git
    GIT_TAG        v0.8.2
    GIT_SHALLOW    TRUE
    SOURCE_SUBDIR  cmake_unofficial
)
set(XXHASH_BUILD_XXHSUM OFF CACHE BOOL "")
set(XXHASH_BUILD_ENABLE_INLINE_API ON CACHE BOOL "")
FetchContent_MakeAvailable(xxhash)

# Provide xxHash::xxhash alias regardless of upstream naming.
if(TARGET xxhash AND NOT TARGET xxHash::xxhash)
    add_library(xxHash::xxhash ALIAS xxhash)
endif()

# ---- nlohmann_json ----
FetchContent_Declare(
    nlohmann_json
    GIT_REPOSITORY https://github.com/nlohmann/json.git
    GIT_TAG        v3.11.3
    GIT_SHALLOW    TRUE
)
set(JSON_BuildTests OFF CACHE BOOL "")
FetchContent_MakeAvailable(nlohmann_json)

# ---- Catch2 (test only) ----
if(EKCHOUS_BUILD_TESTS)
    FetchContent_Declare(
        Catch2
        GIT_REPOSITORY https://github.com/catchorg/Catch2.git
        GIT_TAG        v3.7.1
        GIT_SHALLOW    TRUE
    )
    FetchContent_MakeAvailable(Catch2)
endif()

# ---- Tracy (optional) ----
if(EKCHOUS_ENABLE_TRACY)
    FetchContent_Declare(
        tracy
        GIT_REPOSITORY https://github.com/wolfpld/tracy.git
        GIT_TAG        v0.11.1
        GIT_SHALLOW    TRUE
    )
    set(TRACY_ENABLE ON CACHE BOOL "")
    FetchContent_MakeAvailable(tracy)
endif()

# ---- EnTT (deferred to Phase 1; not used Day-One) ----
# FetchContent_Declare(EnTT GIT_REPOSITORY https://github.com/skypjack/entt.git GIT_TAG v3.13.2)
# FetchContent_MakeAvailable(EnTT)

# ---- zstd (deferred to Phase 1; chunk serialization needs it) ----
# FetchContent_Declare(zstd GIT_REPOSITORY https://github.com/facebook/zstd.git GIT_TAG v1.5.6
#                       SOURCE_SUBDIR build/cmake)
# FetchContent_MakeAvailable(zstd)
