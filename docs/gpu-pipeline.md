# EKCHOUS Engine — GPU Pipeline

The 18 sim passes that run every sim tick (15 Hz) plus the render pass (60 Hz). Every pass is `[DET]` — determinism is the pipeline's design, not a property of any individual pass. `[ID]` = indirect dispatch over active chunks. `[FG]` = full-grid.

All passes are compute except #19. Memory barriers between passes are minimal-mask `glMemoryBarrier`. Double-buffered grid (A/B); ping-pong each tick.

---

## Pass list

### 1. `ChunkActivation` [FG, cheap]
**Purpose**: classify chunks Active/Dormant within the LOD Active band; emit `ChunkActiveList` and `DispatchIndirectArgs`.

**Inputs**: `Grid_R` (read), `ChunkMeta` (read+write activity bits), player position(s).

**Outputs**: `ChunkActiveList` (compact array of chunk IDs), `DispatchIndirect` arg buffers (one per `[ID]` pass).

**Determinism**: Blelloch / Kogge-Stone prefix sum with fixed workgroup 256. Chunk iteration order = Z-order Morton key over `(face, cx, cy)`. No race because the input list is sorted; no float math.

### 2. `ForceReceive` [ID]
**Purpose**: each active pixel sums bonded-neighbour forces from the prior tick's velocity/force buffer.

**Inputs**: `Grid_R`, `BondsBuffer`, `ForceField_prev`.

**Outputs**: `ForceField_curr` (per-pixel net force accumulator).

**Determinism**: read-only of prior frame's buffer; no atomics; one thread per pixel.

### 3. `BondPropagate` [ID]
**Purpose**: kinetic / force propagation along bonded structures, one budget-step per tick (per `pixel_physics_foundation §3.4.5/§3.4.12`).

**Inputs**: `Grid_R`, `BondsBuffer`, `ForceField_curr`.

**Outputs**: `Grid_W` (force / impulse field updates), `PropagationProposalBuf`.

**Determinism**: each propagating "effect token" carries a stable `(source_pixel_id, emit_tick)` key. Conflicting writes to the same target cell resolve by `min(key)`, not first-writer-wins. Token list sorted by stable key before resolve.

### 4. `ChemistryBondResolve` [ID, chunk-gated]
**Purpose**: pairwise neighbour reactivity lookup; emit proposed bond-break / bond-form events.

**Inputs**: `Grid_R`, `MaterialLUT`, `CompoundRecipes`.

**Outputs**: `ChemistryProposalBuf` (per-cell append, atomic counter).

**Determinism**: gated by per-chunk `chemistry_dirty` flag (most chunks skip). Proposals are appended via integer `atomicAdd` only.

### 5. `ProposalSort` [GLOBAL, runs on every per-cell-proposal buffer above]
**Purpose**: deterministic radix sort by `(target_cell, stable_key)`.

**Inputs**: `*ProposalBuf` (raw).

**Outputs**: `*ProposalBuf` (sorted).

**Determinism**: radix sort over fixed-width keys; bitonic fallback only if a key can't fit a workgroup. Workgroup size and lane count fixed at compile time.

### 6. `ChemistryApply` [ID over proposed cells]
**Purpose**: single-writer walk of sorted proposals; apply state transitions; pixel reclassify on compound match (`pixel_physics_foundation §3.7.4`); write `compound_classification_slot`.

**Inputs**: `Grid_R`, `ChemistryProposalBuf` (sorted), `MaterialLUT`, `CompoundRecipes`.

**Outputs**: `Grid_W` (material / state fields), `OutputByproductSpawnBuf` (for byproduct pixels per `chemistry §1.2`).

**Determinism**: one thread per contested cell; conflicts pre-eliminated by sort.

### 7. `ChargePropagate` [ID over nerve-bearing chunks]
**Purpose**: nerve-graph BFS, one tick of source advance at 30 px/tick budget (`organic_tissues §2.8`).

**Inputs**: `Grid_R`, `ChargeField_prev`, `NerveFrontSet_A`.

**Outputs**: `ChargeField_curr`, `NerveFrontSet_B` (next tick).

**Determinism**: front-set stored as sorted array by `pixel_id`; tie on multiple arrivals at one cell resolved by `max(amplitude)` (associative + commutative). Per-tick budget caps wave; frontier chunked into fixed-size workgroups for balance.

### 8. `HeatPropagate` [ID]
**Purpose**: thermal diffusion stencil over bonded neighbours (`pixel_physics_foundation §3.4.11`).

**Inputs**: `Grid_R` (temperature q12.4), `MaterialLUT` (conductivity_thermal), `BondsBuffer`.

**Outputs**: `TemperatureField_next` (q12.4 fixed-point).

**Determinism**: pure stencil into next-tick buffer; no atomics; one thread per pixel.

### 9. `Vascular1DSolve` [ID over pipe-bearing chunks]
**Purpose**: 1D pressure-driven flow along pipe pixels (`organic_tissues §2.9`).

**Inputs**: `PipeTopology_CSR` (extracted at chunk-activation, sorted by `(pipe_segment_id, pixel_index)`), `PressureField_prev` (q16.16 fixed-point), `BodyTable` (pump pressure inputs).

**Outputs**: `PressureField_curr` (q16.16), bleeding-pixel events into `BleedSpawnBuf`.

**Determinism**: fixed-iteration Gauss-Seidel (5 iters) in stable visit order, integer math. No iteration-to-convergence — we don't need it at 15 Hz visual fidelity.

### 10. `RadiationBullets` [ID over in-flight bullets]
**Purpose**: in-flight radiation effect advance (`radiation_and_mutation.md`, `pixel_physics_foundation §3.4.9`).

**Inputs**: `RadiationBulletBuf` (append-only, sorted by spawn tick).

**Outputs**: `RadiationDepositBuf` (proposed deposits per cell).

**Determinism**: per-bullet advance in stable order; deposits sorted by bullet_id before write.

### 11. `InFluxIntegrate` [ID over in_flux pixels]
**Purpose**: q16.16 fixed-point position + q8.8 fixed-point velocity for `in_flux` pixels. **Falling-sand demo lives here.**

**Inputs**: `InFluxParticleSoA` (pos, vel, materialID, bodyID, flags, restCounter), `ForceField_curr`, gravity LUT.

**Outputs**: `InFluxParticleSoA_next`, `RestProposalBuf` (when |v| < ε for K=8 ticks AND ≥1 solid 4-neighbour).

**Determinism**: integer math; no transcendentals; gravity vector read from per-chunk cache, not recomputed.

### 12. `CollisionResolve` [ID over RestProposalBuf]
**Purpose**: snap in_flux particles to grid cells; resolve contention.

**Inputs**: `RestProposalBuf` (sorted from pass 5).

**Outputs**: `Grid_W` (snapped cells), `InFluxParticleSoA` (snapped particles marked dead in free-list).

**Determinism**: atomic-min on `(distance_q16 << 16) | particle_id`. Losers re-queue for next tick (not destroyed). Replaces PixelFlow's `imageAtomicCAS` collision pattern.

### 13. `BodySegmentCCL` [ID, throttled]
**Purpose**: connected-component labeling for body re-segmentation after bond-break events.

**Inputs**: `BondBreakBitset`, `Grid_R`, `BodyTable`.

**Outputs**: `BodyTable` (new body_ids), `Grid_W` (body_id reassignments).

**Determinism**: **incremental** label propagation — only relabel cells whose bond bitfield changed; bond-break enqueues localized relabel costing `O(local_component_size)` not `O(world)`. Fixed iteration count per body-size band (≤16 iters for <1k pixel bodies, ≤64 for chunk-scale). Larger bodies (megafauna) fall back to async CPU relabel over 2–4 ticks.

### 14. `BodyIntegrate` [ID over live bodies]
**Purpose**: integrate per-body motion. **Hybrid dispatch.**

**Inputs**: `BodyTable`, `Grid_R`.

**Outputs**: `BodyTable` (updated COM, vel, orient, AABB), `Grid_W` (rasterized body pixels at new pose).

**Determinism**: integer / fixed-point COM and AABB; rigid integration is closed-form; deformable integration is fixed-iteration Verlet with constraint loop.

- **RIGID** (rocks, metal, ice, inert): integrate COM with linear+angular velocity once; cluster moves as rigid group.
- **DEFORMABLE** (alive tissue, freshly-dead bodies): Verlet per pixel with per-bond stiffness from directional profile and tissue affinity. COM and AABB still computed for spatial queries.

### 15. `DecayTickScheduler` [ID]
**Purpose**: apply per-element decay tick (`decay_system §2.1`).

**Inputs**: `Grid_R`, `MaterialLUT` (decay_tick_interval).

**Outputs**: `Grid_W` (decremented mass; incremented `decay_progress` in 0.01 units), `DecayParticleSpawnBuf`.

**Determinism**: per-pixel decay only when `current_tick % element.decay_tick_interval == 0`. Cheap because most pixels do nothing per tick.

### 16. `DecayFieldDiffuse` [FG, periodic]
**Purpose**: coarse light + scent + radiation grid diffusion (`decay_system §4`, `pixel_physics_foundation §14.1`).

**Inputs**: `DecayParticleField` (sparse, per-field-cell), `LightField`, `MaterialLUT` (em_transmissivity).

**Outputs**: `DecayParticleField_next`, `LightField_next`.

**Determinism**: single Gauss-Seidel sweep in stable scanline order. Not every tick (¼ rate is fine).

### 17. `EMSenseRaycast` [ID over Wisp sensors]
**Purpose**: one ray per Wisp-sensor pair per tick; integrate EM transmissivity along bonded-matter path (`pixel_physics_foundation §8.4`).

**Inputs**: `Grid_R`, `MaterialLUT` (em_transmissivity), `WispSensorList`.

**Outputs**: `WispSensorReadouts` (per-sensor accumulated signal).

**Determinism**: reuses BondPropagate's traversal math with em_transmissivity parameter. One ray per sensor; visibility test only — no scene-wide reduction.

### 18. `SleepPromote` [FG over chunk grid, tiny]
**Purpose**: flip chunks with no in_flux, no chemistry, no body overlap, no wake to dormant within the Active band.

**Inputs**: `ChunkMeta`, `Grid_R`, `BodyTable`.

**Outputs**: `ChunkMeta` (activity bits for next tick).

**Determinism**: fixed-point threshold compare; neighbour-OR for wake propagation (impulse / pressure / heat / charge crossings).

### 19. `LayeredRender` (render-only, 60 Hz, no sim coupling)
**Purpose**: peel-pass cross-section view (skin → muscle → skeleton → organs → Core) per the corpus art direction lock (`INDEX` art-direction).

**Inputs**: `Grid_R` (through page table), `MaterialLUT` (color), `InFluxParticleSoA`, `BodyTable`, `ClothParticleSoA`.

**Outputs**: framebuffer.

**Determinism**: rendering does not feed the next sim tick; non-deterministic float math is acceptable here. Camera/UI interpolation lives here, not in sim.

---

## Pass dependencies

```
  1 ChunkActivation
  │
  2 ForceReceive ----┐
  │                 │
  3 BondPropagate    │
  │                 │  (pass 2 reads previous tick's force; pass 3 writes next tick)
  4 ChemistryBondResolve
  │
  5 ProposalSort (multiple)
  │
  6 ChemistryApply
  │
  7 ChargePropagate
  │
  8 HeatPropagate
  │
  9 Vascular1DSolve
  │
 10 RadiationBullets
  │
 11 InFluxIntegrate
  │
 12 CollisionResolve
  │
 13 BodySegmentCCL
  │
 14 BodyIntegrate
  │
 15 DecayTickScheduler
  │
 16 DecayFieldDiffuse (every 4 ticks)
  │
 17 EMSenseRaycast
  │
 18 SleepPromote
  |
  | --- end of sim tick ---
  |
  | --- 0..3 render frames before next tick ---
  |
 19 LayeredRender (60 Hz, no sim coupling)
```

All arrows are hard memory barriers (`glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT | GL_TEXTURE_FETCH_BARRIER_BIT | GL_ATOMIC_COUNTER_BARRIER_BIT)`). The pipeline never overlaps sim passes within a tick.

## Day-One falling-sand subset

The Day-One falling-sand demo wires passes **11 + 12 + 14-rigid + 19** only. Everything else is stubbed.

- Pass 11 (InFluxIntegrate): gravity-only integration of fallen-sand pixels.
- Pass 12 (CollisionResolve): snap sand to grid when at rest, with deterministic contention resolution.
- Pass 14 rigid path: rigid body present only as a static block of stone the sand falls past.
- Pass 19 (LayeredRender): one layer only, no peel.

Acceptance: 1000-tick replay on the same vendor produces bit-identical xxHash3 on the pixel SSBO every tick.

## Sim-tick budget at 15 Hz

Total budget: 66.7 ms per sim tick.

| Pass | Budget (ms) | Notes |
|---|---|---|
| 1. ChunkActivation | 0.5 | Prefix sum + Morton |
| 2. ForceReceive | 2 | Per-pixel read-only |
| 3. BondPropagate | 5 | Token sort + writes |
| 4. ChemistryBondResolve | 4 | Gated by chemistry_dirty |
| 5. ProposalSort (x5) | 8 | Radix sort multiple buffers |
| 6. ChemistryApply | 3 | Single-writer per cell |
| 7. ChargePropagate | 3 | Only nerve-bearing chunks |
| 8. HeatPropagate | 2 | Stencil |
| 9. Vascular1DSolve | 4 | Pipe-bearing chunks only |
| 10. RadiationBullets | 1 | Sparse |
| 11. InFluxIntegrate | 6 | The hot loop |
| 12. CollisionResolve | 3 | Snap-to-grid |
| 13. BodySegmentCCL | 4 | Incremental only |
| 14. BodyIntegrate | 5 | Hybrid dispatch |
| 15. DecayTickScheduler | 1 | Most pixels skipped |
| 16. DecayFieldDiffuse | 2 | Every 4 ticks → 0.5 avg |
| 17. EMSenseRaycast | 2 | One ray per Wisp sensor |
| 18. SleepPromote | 0.5 | Chunk-level only |
| **Total** | **~56 ms** | Leaves ~10 ms headroom |

Megafauna combat triggers **priority shedding** (`pixel_physics_foundation §11.9`): pass 15 (decay), pass 16 (field diffuse), pass 17 (EM sense) become Low/Standard tier and are shed first.

The 4 ms BodySegmentCCL line is the single highest cost-variance pass. From-scratch CCL on a 600×600 pixel megafauna body would blow the entire budget; the incremental design (only relabel cells with changed bond bitfields) is what makes it tractable.
