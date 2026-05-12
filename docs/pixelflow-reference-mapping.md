# EKCHOUS Engine — PixelFlow → C++ Module Mapping

PixelFlow (Thomas Diewald's Java/Processing/GLSL library, mirrored in this repo's `src/com/thomasdiewald/pixelflow/`) is the **fidelity reference** for this engine — not a port target. Its mature GLSL Stam fluid solver, Verlet softbody, GPU flow-field particles, and dwgl OpenGL wrappers are the bar; the C++ engine matches or exceeds them.

**Critical caveat**: several PixelFlow subsystems are non-deterministic and CANNOT be ported directly. They must be replaced. See §2.

## 1. Direct mappings (port + adapt)

| PixelFlow Java path | C++ namespace / dir | Notes |
|---|---|---|
| `src/com/thomasdiewald/pixelflow/java/DwPixelFlow.java` | `engine::Engine` (`engine/engine/Engine.{h,cpp}`) | Top-level orchestrator: GL context, scheduler, frame loop |
| `src/com/thomasdiewald/pixelflow/java/dwgl/*` | `engine::gpu` (`engine/gpu/`) | OpenGL wrappers: Shader, Texture, Buffer, Framebuffer, Query, ShaderPreprocessor |
| `src/com/thomasdiewald/pixelflow/java/softbodydynamics/{particle,constraint,softbody}` | `engine::sim::cloth` (`engine/sim/cloth/`) | Verlet integration + distance/bend constraints — used for cloth/rope/webbing AND deformable body integration (the corpus combines these via hybrid integration_mode) |
| `src/com/thomasdiewald/pixelflow/java/flowfieldparticles/*` | `engine::sim::passes::InFluxIntegrate` | Generalized GPU particle update repurposed for off-grid `in_flux` pixels |
| `src/com/thomasdiewald/pixelflow/java/accelerationstructures/DwCollisionGrid.java` | `engine::sim::broadphase` (`engine/sim/broadphase/`) | Hybrid chunk + uniform-grid spatial hash. **Rewrite with fixed-point keys**; float keys are non-deterministic |
| `src/com/thomasdiewald/pixelflow/java/imageprocessing/filter/*` | `engine::gpu::post` (`engine/gpu/post/`) | Convolution / blur / morphology / distance transforms |
| `src/com/thomasdiewald/pixelflow/java/antialiasing/{FXAA,GBAA,SMAA}` | `engine::gpu::post::aa` (`engine/gpu/post/aa/`) | SMAA = default. **Render-only, never sim** — float math allowed |
| `src/com/thomasdiewald/pixelflow/java/utils/*` | `engine::core` (`engine/core/`) | Logger, threading, shader preprocessor, uniform cache, math utilities (**fixed-point only**), perf counters |
| `src/com/thomasdiewald/pixelflow/glsl/Filter/*` | `shaders/glsl/post/filter/` | Direct GLSL port — these are render-side filters |
| `src/com/thomasdiewald/pixelflow/glsl/antialiasing/SMAA/*` | `shaders/glsl/post/aa/smaa/` | Direct GLSL port — render-side |

## 2. PixelFlow subsystems that MUST be REPLACED (non-deterministic)

| PixelFlow subsystem | Why non-deterministic | Replacement | Where in pipeline |
|---|---|---|---|
| **Stam jacobi fluid solver** (`src/com/thomasdiewald/pixelflow/java/fluid/DwFluid2D.java` + `glsl/Fluid/*.frag`) | Iterates Jacobi to convergence with float accumulation; iteration count varies by initial state; float reductions vendor-sensitive | **Drop entirely.** Vascular = 1D Gauss-Seidel solver (gpu-pipeline.md pass 9). Atmosphere / gas = decay-particle field diffusion (`EKCHOUS_decay_system.md §4.11`). Smoke/steam buoyancy = per-pixel `in_flux` upward bias scaled by density delta | N/A — removed |
| **Atomic-grid collision** (in `DwCollisionGrid.java`) | First-writer-wins via `imageAtomicCAS` is implementation-defined ordering | Propose-then-resolve (gpu-pipeline.md passes 11–12) | `CollisionResolve` pass |
| **Particle hash with float keys** (`flowfieldparticles/DwFlowFieldParticles.java`) | Float comparisons differ across vendors; hash bucket assignments can vary | Fixed-point cell keys (`int_x \| int_y << 16`); deterministic bucket sort with overflow-drop by lowest pixel_id | `engine::sim::broadphase` |

## 3. PixelFlow subsystems we explicitly DO NOT port

These provide capability EKCHOUS doesn't need:

| PixelFlow subsystem | Why skip |
|---|---|
| `OpticalFlow/*` (Lucas-Kanade optical flow) | EKCHOUS is gameplay sim; no use case for motion estimation in a side-scroller |
| `HarrisCornerDetection/*` | No feature-detection use case |
| `render/Skylight/*` (3D AO + shadow mapping) | EKCHOUS is 2D side-scroller; lighting is the 2D light grid (`pixel_physics_foundation §14`) |
| `geometry/HalfEdge/*` (mesh structures) | No mesh data in EKCHOUS — everything is per-pixel |
| `rigid_origami/*` | Origami simulation is not in scope |
| `Streamlines/*` (Line Integral Convolution) | Visual technique not needed |
| `sampling/*` (Poisson disk etc.) | Sampling is deterministic-seeded RNG, not Poisson |

## 4. New engine modules with no PixelFlow analog

These are EKCHOUS-specific subsystems that the corpus requires; PixelFlow has nothing comparable.

| C++ module | Purpose | Corpus reference |
|---|---|---|
| `engine::sim::elements` | 16-element + compound LUT, JSON loader | `INDEX` open backlog #1; `pixel_physics_foundation §10` |
| `engine::sim::tissues` | 9 tissue profiles, self/cross affinity, directional bonds | `EKCHOUS_organic_tissues.md §2` |
| `engine::sim::passes::BondPropagate` | Force / kinetic propagation along bonds with stable-key tokens | `pixel_physics_foundation §3.4` |
| `engine::sim::passes::ChargePropagate` | Nerve-graph BFS at 30 px/tick | `EKCHOUS_organic_tissues.md §2.8` |
| `engine::sim::passes::HeatPropagate` | Thermal diffusion stencil | `pixel_physics_foundation §3.4.11` |
| `engine::sim::passes::Vascular1DSolve` | 1D pressure-driven flow in pipe pixels | `EKCHOUS_organic_tissues.md §2.9` |
| `engine::sim::passes::RadiationBullets` | In-flight radiation effect advance | `EKCHOUS_radiation_and_mutation.md` |
| `engine::sim::passes::ChemistryBondResolve` + `ChemistryApply` | 5-tier reaction matrix with output distributions | `EKCHOUS_chemistry.md §1` |
| `engine::sim::passes::DecayTickScheduler` + `DecayFieldDiffuse` | Per-element decay ticks + particle field | `EKCHOUS_decay_system.md` |
| `engine::sim::passes::EMSenseRaycast` | Per-Wisp-sensor raycast through bonded matter | `pixel_physics_foundation §8.4` |
| `engine::sim::bodies::Integrate` | Hybrid rigid + deformable body integration | `engine_integration_addendum.md §1.2` |
| `engine::sim::bodies::CCL` | Incremental connected-component labeling | Same as above |
| `engine::sim::permeability` | Permeable host + guest pixel mechanics | `engine_integration_addendum.md §1.3` |
| `engine::world::Chunk` + `PageTable` + `Streamer` + `Serializer` | Cubed-sphere chunk paging + Zstd disk persistence | `EKCHOUS_planet_satellites.md` 5-chunk rule; `pixel_physics_foundation §11.2` LOD bands |
| `engine::sphere` | Cubed-sphere face math + face-adjacency LUT + gravity vector generator | `EKCHOUS_world_system.md §5.6`; `INDEX` Planet entry |
| `engine::determinism` | Fixed-point types, golden hash, per-pass checksum, banned-ops lint | `engine_integration_addendum.md §2.4`; this doc |
| `engine::net` | 4-phase multiplayer handoff stubs | `EKCHOUS_multiplayer.md §5.3` |
| `engine::mat::CompoundRecipes` | 5-tier chemistry recipes | `EKCHOUS_chemistry.md §1–7` |
| `engine::render::Layered` | Cross-section peel rendering | `INDEX` art-direction lock |

## 5. GLSL shader port status

| PixelFlow GLSL path | Action | Replacement / target |
|---|---|---|
| `glsl/Fluid/jacobi.frag` | DELETE | Stam solver dropped |
| `glsl/Fluid/advect.frag` | DELETE | Stam solver dropped |
| `glsl/Fluid/divergence.frag` | DELETE | Stam solver dropped |
| `glsl/Fluid/gradient.frag` | DELETE | Stam solver dropped |
| `glsl/Fluid/vorticity.frag` | DELETE | Stam solver dropped |
| `glsl/ParticleSystem/*` | REWRITE | `shaders/glsl/sim/in_flux_integrate.comp` (compute, fixed-point) |
| `glsl/FlowFieldParticles/*` | REWRITE | Subset goes into in_flux integration; flow-field abstraction not needed |
| `glsl/Filter/*` | PORT AS-IS | `shaders/glsl/post/filter/*` (render-only) |
| `glsl/antialiasing/SMAA/*` | PORT AS-IS | `shaders/glsl/post/aa/smaa/*` (render-only) |
| `glsl/render/*` (skylight) | DELETE | 3D lighting not applicable |
| `glsl/Streamlines/*` | DELETE | Not used |
| `glsl/HarrisCornerDetection/*` | DELETE | Not used |
| `glsl/OpticalFlow/*` | DELETE | Not used |

## 6. Stub macros

Every C++ module that is stubbed Day-One contains a `TODO_PIXELFLOW_PORT` macro pointing at the originating Java file, plus a `TODO_CORPUS` macro pointing at the relevant corpus section. Examples:

```cpp
// engine/sim/cloth/Verlet.cpp
void ClothVerletStep(ClothParticleSoA& cloth, float dt) {
  TODO_PIXELFLOW_PORT("softbodydynamics/particle/DwParticle2D.java#update()");
  TODO_CORPUS("engine_integration_addendum::1.4");
  // ... empty stub
}
```

The `grep -rn 'TODO_PIXELFLOW_PORT\|TODO_CORPUS' engine/` audit (verification step #6 in the plan) confirms every stub points somewhere meaningful.
