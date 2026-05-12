# EKCHOUS engine (C++/OpenGL)

This directory contains the C++/OpenGL pixel physics engine for EKCHOUS, layered alongside the existing PixelFlow Java/GLSL reference library at the repo root in `src/com/thomasdiewald/pixelflow/`.

**Design docs**: `../docs/`
- `architecture.md` — top-level engine architecture
- `gpu-pipeline.md` — the 18 sim passes
- `data-layout.md` — GPU resource schemas
- `determinism.md` — fixed-point, banned ops, golden-hash test strategy
- `pixelflow-reference-mapping.md` — Java/GLSL → C++ module map
- `streaming.md` — cubed-sphere chunk paging
- `open-questions.md` — engine-specific risks

**Game-design docs** live in the companion repo `vinceclipse/ekchous`.

## Quick start

```bash
cmake -B build -G Ninja
cmake --build build

# Run the falling-sand demo (Day-One):
./build/ekchous

# Run the determinism replay gate (1000 ticks, same-vendor):
./build/ekchous --replay-test=1000

# Run unit tests:
ctest --test-dir build
```

## Module layout

```
engine/
  core/             common types + logger
  determinism/      fixed-point types, golden hash, seeded RNG, shader linter
  sphere/           cubed-sphere face math + adjacency LUT
  gpu/              OpenGL 4.3 wrappers (Shader, Buffer, Texture, Framebuffer)
  engine/           top-level Engine class (GLFW boot + frame loop)
  sim/
    grid/           per-chunk pixel grid
    passes/         18 sim passes (1 working Day-One, 17 stubbed)
    elements/       16-element + compound LUT (loaded from assets/elements/)
    tissues/        9 tissue profiles
    bodies/         rigid + deformable body integration
    broadphase/     fixed-point spatial hash (Phase 1+)
    cloth/          Verlet for cloth/rope/webbing (Phase 1+)
  world/            chunk paging + streaming (Phase 1+)
  mat/              compound recipes (Phase 1+)
  net/              4-phase multiplayer handoff stubs (Phase 6+)
  render/           cross-section peel rendering (Phase 1+)
  main.cpp          executable entry point
```

Every stubbed module contains `TODO_CORPUS(<doc>::<section>)` and `TODO_PIXELFLOW_PORT(<java-path>)` macros pointing at the originating reference.

## Day-One acceptance

The Day-One falling-sand demo wires only passes 11 (`InFluxIntegrate`) + 12 (`CollisionResolve`) + 14 rigid path + 19 (`LayeredRender`).

**Acceptance gate**: `./build/ekchous --replay-test=1000` runs the demo for 1000 ticks twice on the same machine and asserts bit-identical xxHash3 checksums per tick. Failure aborts the process with bisect info.
