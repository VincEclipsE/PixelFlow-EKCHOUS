# EKCHOUS Pixel Physics Engine — Architecture

C++/OpenGL implementation of the EKCHOUS pixel physics simulation. Corpus-aligned (the 23-doc EKCHOUS design corpus at `vinceclipse/ekchous`), determinism-first, GPU-driven.

## Purpose

Produce a high-fidelity, bit-deterministic per-pixel simulation that minimally matches Noita's physics quality, plus the five EKCHOUS-specific extensions: off-grid pixel motion with snap-to-grid at rest; atomically bonded rigid + deformable bodies; permeable/absorptive materials; cloth/rope/webbing via Verlet; and GPU chunked sleep.

This is not a port of PixelFlow. PixelFlow is a fidelity reference (mature GLSL Stam fluid, Verlet softbody, GPU particles) but its key solvers are non-deterministic across vendors and must be replaced.

## Constraints (binding)

| Constraint | Source | Implication |
|---|---|---|
| Sim is **15 Hz**, render is **60 Hz** | `EKCHOUS_pixel_physics_foundation.md §5`, `INDEX` | World state is NOT interpolated between sim ticks; only camera/cursor/UI interpolate in the render encoder |
| **Deterministic GPU sim from Day 1** | `engine_integration_addendum.md §2.4` | Fixed-point math, no transcendentals, no first-writer-wins atomics |
| **OpenGL 4.3+ compute shaders** | Session-locked | All sim work in `glDispatchCompute` / `glDispatchComputeIndirect` |
| **Cubed-sphere planets** | `INDEX` Planet, `world_system.md §5.6` | Chunk address `(face: u3, cx: u20, cy: u20)`; 24-entry face-adjacency LUT |
| **Per-position gravity** | `pixel_physics_foundation §3.5` | Cached at chunk-scale, recomputed at chunk activation |
| **16 first-pass elements + compounds** | `INDEX` open backlog #1 | `element_id: u8` indexes a 256-entry LUT; first-pass JSON ships 16 entries |
| **9 organic tissues + directional bonds** | `organic_tissues.md §2` | Tissue self/cross affinity multiplies base bond strength |
| **4 pixel states**: alive/dead/in_flux/inert | `pixel_physics_foundation §7` | 2 bits in the per-pixel struct |
| **5-tier chemistry with output distributions** | `chemistry.md §1.2` | Reactions emit multiple byproduct elements per consumed pixel |
| **Per-element decay tick intervals** | `decay_system.md §2.1` | Tick scheduler fires only when `current_tick % decay_tick_interval == 0` |
| **Charge propagation 30 px/tick on nerve** | `organic_tissues.md §2.8` | BFS-style wave with stable front-set; budget = 30 per tick |
| **Vascular 1D pressure system** | `organic_tissues.md §2.9`, `pixel_physics_foundation §3.4.10` | Pipe topology extracted to CSR at chunk-activation; fixed Gauss-Seidel iterations |
| **EM/sense line-of-effect** | `pixel_physics_foundation §8.4` | Per-Wisp-sensor raycast through bonded matter, integrating EM transmissivity |
| **4-phase multiplayer handoff** | `multiplayer.md §5.3` | Engine exposes hooks; determinism makes the handoff cheap |

## High-level system map

```
                          ┌──────────────────────┐
                          │  engine::Engine    │ ◀── main.cpp
                          │  (orchestrator)    │
                          └─────┬───────────────┘
              ┌───────────┼────────────┐
              ▼                               ▼
  ┌─────────────────────┐    ┌─────────────────────┐
  │  engine::sim         │    │  engine::render      │
  │  (15 Hz, 18 passes)  │    │  (60 Hz, peel pass)  │
  └───────────┬──────────┘    └─────────────────────┘
              │
   ┌───────────────────────────────────────┐
   ▼                  ▼                  ▼                    ▼
┌──────────┐ ┌──────────┐ ┌──────────────┐ ┌─────────────────┐
│ sim::    │ │ sim::    │ │ sim::bodies   │ │ sim::passes::*  │
│ grid     │ │ elements │ │ (RIGID/DEFORM)│ │ (1..18 below)   │
└──────────┘ └──────────┘ └──────────────┘ └─────────────────┘

     engine::world        engine::sphere       engine::determinism
     (chunk paging)       (cubed-sphere)       (fixed-point, golden hash)
```

Four binding layers under `engine::Engine`:

1. **`engine::sim`** — the 18-pass GPU pipeline (see `gpu-pipeline.md`).
2. **`engine::render`** — 60 Hz peel-pass renderer with cross-section camera (see `gpu-pipeline.md` pass 19).
3. **`engine::world`** — chunk paging, streaming, serialization (see `streaming.md`).
4. **`engine::determinism`** — fixed-point types, banned-ops lint, xxHash3 golden-hash, per-pass checksum bisect tooling (see `determinism.md`).

Plus three support layers:

- **`engine::core`** — logger, profiler, job system, RNG (seeded).
- **`engine::gpu`** — OpenGL wrappers (Shader, Texture, Buffer, Framebuffer, Query). Ports of PixelFlow's `dwgl/`.
- **`engine::sphere`** — cubed-sphere face math, gravity LUT generator, seam unit-test.

## Determinism is the architecture

Every non-trivial architectural decision is shaped by the determinism contract. Examples:

- **Stam fluid solver is core substrate, with engineered-in determinism.** The corpus engine architecture (`EKCHOUS_engine_architecture.md`) names PixelFlow-style CFD — i.e. Stam stable-fluids — as the substrate, and the live code at `engine/sim/softbody/Fluid2D.cpp` is a complete Stam implementation. The Stam solver drives almost every higher-level system: atmosphere (per-cell gas density advected by the velocity field), wind, smoke, scent diffusion (the decay-particle field is *advected by* the fluid, not a replacement for it), heat propagation, and the generalized field-solver use cases (`EKCHOUS_engine_architecture.md` §3.7) including flow-field-as-pathfinding. **Determinism strategy:** fixed-iteration Gauss-Seidel relaxation (typically 4-20 passes, identical count every run — no float-convergence loops), fixed-point pressure / divergence accumulators where bit-identical cross-vendor matters, deterministic boundary application order, no `imageAtomicCAS` in fluid updates. **Cross-vendor fallback:** if GPU fluid proves unachievable as bit-identical across vendors, deterministic-CPU Stam at 64×64 is ~1-2 ms per tick at 15 Hz — well within budget. Vascular: 1D Gauss-Seidel with fixed-point pressure on the pipe topology.
- **Propose-then-resolve everywhere.** `imageAtomicCAS` first-writer-wins is banned. Every contended write goes through a per-cell proposal buffer, gets sorted by stable key, and is applied by one writer per cell.
- **No transcendentals.** `sin/cos/exp/log/pow` are LUTs, not hardware ops. Gravity direction is precomputed at chunk-activation, not recomputed per pixel.
- **No float reductions on user-visible state.** COM, total_mass, angular_inertia, etc. accumulate in fixed-point integer accumulators via Kogge-Stone prefix sum; float is read out only at use-time. Fluid intermediate state (velocity, density) may use float on the GPU pass provided the iteration count is fixed and the output is hashed at quantized precision for the golden-hash test.

This is more expensive than the float-everywhere fast path, but the corpus's save format (seed + modifier-delta), the multiplayer 4-phase handoff (`multiplayer §5.3`), and the corpus-mandated cross-platform determinism contract (`world_system §2`) all require it.

## Phase 0 deliverable

The Day-One falling-sand demo (passes 11 + 12 + 14-rigid + 19) is the executable Phase 0 determinism tech-spike. Acceptance: 1000-tick replay produces bit-identical xxHash3 per tick across two consecutive runs on the same vendor. Cross-vendor matrix runs in CI in Phase 1.

Everything else is documented and stubbed with `TODO_CORPUS(<doc>::<section>)` and `TODO_PIXELFLOW_PORT(<java-path>)` macros pointing at the originating reference.

## What this engine deliberately is NOT

- **Not a Noita clone.** Noita's checker-pattern CPU sim, falling-sand-only material model, and non-deterministic GPU shader trick are reference points but not blueprints. EKCHOUS is GPU-deterministic and richer in material model (16+ elements, 9 tissues, vascular, charge, chemistry, decay).
- **Not a general-purpose physics engine.** No 3D, no continuum mechanics, no constraint solvers for joints. Verlet + per-bond stiffness is the only deformable solver; rigid bodies use 2D linear+angular integration only.
- **Not a Vulkan/wgpu port.** OpenGL 4.3+ compute is the binding target. Vulkan is an explicit non-goal for the first release.
- **Not networked physics.** The 4-phase handoff is state-snapshot transfer + deterministic forward-sim, not network rollback. No lockstep, no client prediction.

## File map

- `docs/architecture.md` — this doc.
- `docs/gpu-pipeline.md` — the 18 sim passes + render pass, with barriers and dispatch modes.
- `docs/data-layout.md` — per-pixel SSBO packing, side-buffers, LUTs, body table.
- `docs/determinism.md` — atomic discipline, fixed-point types, banned ops, hash test strategy.
- `docs/pixelflow-reference-mapping.md` — Java/GLSL → C++ module map + replacements.
- `docs/streaming.md` — cubed-sphere chunk paging, 3-tier residency, body/cloth survival.
- `docs/open-questions.md` — engine-specific risks with recommended defaults.

Companion docs on `vinceclipse/ekchous@claude/review-game-concept-Tbibx`:
- `docs/REVIEW.md` — review of the 23-doc design corpus.
- `docs/engine_integration_addendum.md` — the five engine non-negotiables + seven session-locked decisions, formally integrated.
