# PixelFlow Studio

A node-based composition app for GPU graphics primitives, built on a hard fork of [Thomas Diewald's PixelFlow](https://github.com/diwi/PixelFlow) library — decoupled from Processing, running on pure JOGL.

**Status:** early development, milestone M0 (repo scaffold).

## What it is

Open the app, see a palette of ~70 GPU compute primitives (fluid sims, flow fields, particles, post-process filters, anti-aliasing, softbody, optical flow). Drag them onto a canvas, wire their typed input/output ports into a branching DAG, tweak parameters on a side panel, watch the result render live.

When you've built something good, save the composition as a new compound tool. It appears in the palette alongside primitives — indistinguishable from a built-in. The library grows over time. Tools make tools.

## Architecture

Three Gradle modules:

```
modules/
  pixelflow-core/    Forked PixelFlow Java source, decoupled from PApplet.
                     Pure JOGL. Same compute primitives, no Processing.
  engine-runtime/    JOGL NEWT host. RenderTarget abstraction.
                     Resource loader. Frame loop.
  studio-app/        DAG runtime (Node/Port/Graph + topological execution +
                     compound nodes), serialization (.pftool / .pflow JSON),
                     Swing/FlatLaf UI shell with imgui-node-editor canvas.
```

## Build

Requires JDK 17. From the repo root:

```
./gradlew build
./gradlew :engine-runtime:run -PmainClass=studio.engine.Smoke        # M1 smoke test
./gradlew :studio-app:run -PmainClass=studio.headless.HeadlessSmoke  # M2 headless render
./gradlew :studio-app:run                                            # M3 full UI
./gradlew :studio-app:packageExe                                     # M4 Windows installer
```

## Milestones

- **M0** — Repo cleanup + Gradle skeleton ✅
- **M1** — Engine layer: decouple PixelFlow from PApplet; smoke test renders fluid in a JOGL window
- **M2** — DAG runtime: typed-port graph, compound nodes, headless `.pflow` execution
- **M3** — Studio UI: Swing + FlatLaf shell, imgui-node-editor canvas, tool palette, parameter panel, save-as-tool wizard
- **M4** — Packaging: jpackage Windows installer with bundled JRE

## Repository history

This repo was previously the work-in-progress **EKCHOUS** C++/OpenGL pixel physics game engine. The C++ implementation was wiped to make room for PixelFlow Studio (Java/JOGL), but the EKCHOUS design corpus is preserved under `docs/` as future direction.

The Studio's DAG runtime is intentionally not bound to PixelFlow primitives — a future content track can add EKCHOUS-aligned game-content nodes (elements, compounds, tissues, bodies) as new primitive types plugging into the same graph engine.

## License

MIT. PixelFlow itself is MIT-licensed by Thomas Diewald (see `modules/pixelflow-core` source headers).
