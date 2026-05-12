# glad2 (OpenGL loader)

This directory needs the glad2-generated OpenGL 4.3 core loader files. They are
**not** fetched via CMake `FetchContent` because the glad2 generator is a Python
script run on the developer machine, not a git submodule.

## Generating glad2 for EKCHOUS

### Option A: web generator (easiest)

1. Visit https://gen.glad.sh/ (the glad2 web service).
2. Set:
   - **Generator**: C/C++
   - **APIs**: gl 4.3 (Core profile)
   - **Extensions**: include at least:
     - `GL_ARB_compute_shader`
     - `GL_ARB_shader_storage_buffer_object`
     - `GL_ARB_shader_image_load_store`
     - `GL_KHR_debug`
     - `GL_KHR_shader_subgroup` (optional, used for proposal-resolve perf)
   - **Options**: "On-demand loader" off; "Loader" on; "Header only" off.
3. Download the archive and extract here so the layout is:

```
third_party/glad/
  README.md                  (this file)
  include/glad/gl.h
  include/KHR/khrplatform.h
  src/gl.c
```

### Option B: glad2 CLI

```
pip install --user glad2
mkdir -p third_party/glad
glad --api gl:core=4.3 \
     --extensions GL_ARB_compute_shader,GL_ARB_shader_storage_buffer_object,GL_ARB_shader_image_load_store,GL_KHR_debug \
     --out-path third_party/glad \
     c
```

## What CMake expects

The top-level `cmake/deps.cmake` looks for `third_party/glad/include/glad/gl.h`.
If it doesn't find it, the build falls back to an INTERFACE-only stub so the
rest of the project still compiles (the Day-One falling-sand demo uses the
CPU-reference path and doesn't strictly need glad).

Once glad is vendored, you'll need a small tweak to `cmake/deps.cmake`:

```cmake
add_library(glad STATIC
    third_party/glad/src/gl.c
)
target_include_directories(glad PUBLIC third_party/glad/include)
```

## Why not include the generated files in this commit?

The generated files contain a copyright + license header from Khronos; the project
maintainers' preference (per Contributing Guidelines.md) is to keep auto-generated
third-party code out of the main commit history and rely on the build to fetch/
generate it. The Day-One CPU-reference demo doesn't need glad, so this is
adequate for verifying the build path is correct.
