# EKCHOUS Engine — Determinism Strategy

Bit-identical GPU sim execution across NVIDIA / AMD / Intel from Day 1. This is the binding contract; every architectural decision flows from it.

## Why determinism is binding

Three corpus requirements force it:

1. **Save format = seed + modifier-delta** (`world_system.md §3`). Replay-from-seed-and-input requires bit-identical forward execution.
2. **Multiplayer 4-phase handoff** (`multiplayer.md §5.3`). Receiver simulates forward to checksum match from a single state snapshot.
3. **Cross-platform determinism Phase 0 tech spike** (`dev_plan.md`). Explicit Phase 0 blocker.

The corpus's earlier formulation — "cross-platform may require fixed-point math" (`world_system §2`) — has been upgraded to BINDING per `engine_integration_addendum.md §2.4`.

## Atomic discipline

### Allowed
- Integer `atomicAdd` on counters (proposal-buffer append-index, body-id allocator).
- Integer `atomicMin` / `atomicMax` (associative + commutative; used for stable-key conflict resolution).
- `memoryBarrierShared()`, `barrier()` within workgroup.
- `memoryBarrierBuffer()` / `memoryBarrierImage()` between dependent passes.

### Banned
- `imageAtomicCAS` first-writer-wins (cross-vendor ordering implementation-defined). **The single highest-value ban.**
- Float atomics (`atomicAdd` on float, where available).
- `atomicExchange` for cell claims.
- `atomicCompSwap` for any cell-resolution pattern.

### Pattern: propose-then-resolve

Every contended write goes through two phases.

**Phase 1 — propose**:
```
uint slot = atomicAdd(proposal_counter[chunk_id], 1);
proposal_buf[chunk_id][slot] = ProposalEntry{
  .target_cell = target,
  .stable_key  = makeStableKey(source_pixel_id, emit_tick),
  .payload     = ...
};
```

**Phase 2 — resolve** (after `ProposalSort` pass):
```
// One thread per contested cell
ProposalEntry winner = proposal_buf[chunk_id][first_index_for_cell[cell]];
grid_w[cell] = applyProposal(grid_r[cell], winner);
```

The sort key is stable (deterministic across runs); the resolve step is single-writer (no race).

## Reduction strategy

### Banned
- Naive parallel float sum-reduction (`a + b + c + d` order varies across vendors).
- Hierarchical reductions across workgroups via shared atomics with floats.

### Used
- **Kogge-Stone prefix sum**, fixed workgroup 256, lane count fixed at compile time.
- Float aggregates (COM, total_mass) accumulate in **fixed-point integer accumulators**; convert to float only at use-time.
- Multi-level reductions use the same fixed group size at every level.

Example COM reduction (per body):
```glsl
// Phase 1: per-workgroup accumulate in fixed-point
shared i64 com_x_sum, com_y_sum, mass_sum;
if (lane == 0) { com_x_sum = 0; com_y_sum = 0; mass_sum = 0; }
barrier();
atomicAdd(com_x_sum, int64_t(pixel.pos.x_q16_16) * pixel.mass_q16_16);
atomicAdd(com_y_sum, int64_t(pixel.pos.y_q16_16) * pixel.mass_q16_16);
atomicAdd(mass_sum, pixel.mass_q16_16);
barrier();
// Phase 2: workgroup writes to global per-body fixed-point accumulator
// (also fixed-point atomicAdd on 64-bit integer)
```

GLSL doesn't have `atomicAdd` on 64-bit integers in core 4.3, so this is split into two 32-bit operations with carry detection — or upgraded to `GL_NV_shader_atomic_int64` (vendor-specific) gated by feature detection with fallback path.

## Fixed-point types

In `engine/determinism/fixed_point.h`:

```cpp
using q16_16 = int32_t;  // pixel units; precision ~15 μpx; range ±32768 px
using q8_8   = int16_t;  // px/tick velocity; range ±128 px/tick
using q12_4  = int16_t;  // Kelvin temperature; range ±4000 K
using q16_16_u = uint32_t; // pressure, mass (non-negative)

constexpr q16_16 to_q16_16(float f) { return static_cast<q16_16>(f * 65536.0f); }
constexpr float  from_q16_16(q16_16 q) { return float(q) / 65536.0f; }
// ... etc.
```

### Where fixed-point is mandatory
- All pixel positions while `in_flux`
- All pixel velocities
- All pixel temperatures
- All charge field values
- All pressure field values
- All body COM / mass / angular inertia
- All AABB corners
- All bond stiffness / elasticity
- All decay_progress fractions

### Where float is allowed
- Rendering inputs (camera matrix, screen-space positions, UI)
- Sensor readouts displayed to player
- Audio mixing
- Anything that does NOT feed the next sim tick

## IEEE-754 cross-vendor hazards

All banned in sim shaders.

| Hazard | Why | Replacement |
|---|---|---|
| `fma(a, b, c)` | Vendor-inconsistent (NVIDIA enables FMA contract by default; AMD requires explicit; Intel varies) | Explicit `a*b + c` — compiler is bound by source order |
| `pow`, `exp`, `log` | Not bit-identical across vendors | LUT indexed by fixed-point input |
| `sin`, `cos`, `tan` | Not bit-identical across vendors | LUT (256-entry quarter-wave table is usually enough) |
| `rsqrt` | Not identical across vendors (hardware approximations differ) | `1.0/sqrt(x)` — `sqrt` is conforming-IEEE on all major GPUs |
| Subnormals | Flush-to-zero behavior varies | Explicitly disable subnormals at boot; assert via `glGetFloatv` or vendor query |
| Order of summation | Float associativity fails | Fixed-point accumulators dodge entirely |
| Loop iteration count | If a loop iterates to convergence with float tolerance, count varies | Fixed iteration count; tolerance never used |

## Lint: banned-ops static check

`engine/determinism/banned_ops.lint.cpp` is a tiny CMake-time tool that scans every `.comp` shader in `shaders/glsl/` and rejects any occurrence of:

- `fma\s*\(`
- `pow\s*\(`
- `exp\s*\(`
- `log\s*\(`
- `sin\s*\(`, `cos\s*\(`, `tan\s*\(`
- `rsqrt\s*\(` / `inversesqrt\s*\(`
- `imageAtomicCAS\s*\(`
- `atomicExchange\s*\(`
- `atomicCompSwap\s*\(`

The rendering shaders (in `shaders/glsl/render/`) are excluded from the lint — they're allowed to use the full GLSL surface.

## Test strategy

### Per-tick golden hash

```cpp
// engine/determinism/golden_hash.cpp
uint64_t hash_grid_buffer(const Grid& g) {
  return XXH3_64bits(g.pixel_data(), g.byte_count());
}

// At end of each sim tick:
uint64_t tick_hash = hash_grid_buffer(grid);
golden_log[tick_index] = tick_hash;
```

Run the falling-sand replay scenario twice on the same machine: both runs MUST produce identical `golden_log[]`.

### Per-pass checksums (bisect tooling)

When the golden hash diverges, per-pass checksums localize the divergence to one pass:

```cpp
// After each compute pass:
if (DEBUG_DETERMINISM) {
  uint64_t pass_hash = hash_grid_buffer(grid);
  if (pass_hash != reference_per_pass[tick][pass_id]) {
    abort_with_bisect_info(tick, pass_id);
  }
}
```

In release builds, only the per-tick hash runs. The per-pass mode is for diagnosis.

### Cross-vendor CI matrix (Phase 1+)

Fixed scenario set:
- `falling_sand_seed_42.scenario` — the Day-One demo, 1000 ticks.
- `single_critter_combat.scenario` — a Phase 1 test with one critter taking damage.
- `megafauna_nerve_pulse.scenario` — a Phase 2+ test with charge propagation across a large nerve.

For each scenario, run on:
- NVIDIA (e.g., RTX 3060)
- AMD (e.g., RX 6700 XT)
- Intel (e.g., Arc A750)
- Mesa software fallback (LLVMpipe)

All runs must produce identical `golden_log[]` per scenario. Failure = vendor-specific bug, investigate.

### Day-One acceptance gate

`./build/pixelflow-ekchous --replay-test=1000` runs `falling_sand_seed_42.scenario` for 1000 ticks twice on the same machine and asserts `golden_log[]` identical. Failure aborts with bisect info. Same-vendor only at Day-One; cross-vendor in Phase 1+.

## Known residual risks

### GPU CCL determinism (the hardest unsolved problem)

Fixed-iteration label propagation works for sub-megafauna bodies (~10k pixels reach stable labels in ≤64 iters). For megafauna fortresses (~600+ pixel diameter) we may need 256+ iters per tick — too expensive for the 4 ms budget.

**Recommended default**: incremental segmentation maintains body labels across ticks. Only relabel cells whose bond bitfield changed. This is the only realistic way to hit budget. Status: design-locked, not yet stressed.

### Persistent-mapped buffer ring depth

3-frame ring matches GL driver default pipeline depth. Determinism doesn't care about ring depth itself, but does care that the CPU never reads a buffer that's still being written by the GPU. `glClientWaitSync` + fences enforce ordering.

### RNG seeding

All random decisions seeded from `(world_seed, tick_number, pixel_id)` via a hash function (xxHash3 of the tuple). No `Math.random()`, no `frand`, no `glsl-noise`. The hash is implemented identically in CPU and GPU code paths.

## File layout under `engine/determinism/`

```
engine/determinism/
  fixed_point.h           — q16_16, q8_8, q12_4 types + conversions
  fixed_point.cpp         — unit tests
  golden_hash.h           — xxHash3 wrapper
  golden_hash.cpp
  per_pass_checksum.h     — debug-mode bisect tooling
  per_pass_checksum.cpp
  banned_ops.lint.cpp     — CMake-time shader linter
  rng_seeded.h            — deterministic RNG (xxHash3 of (seed, tick, pixel_id))
  rng_seeded.cpp
  README.md               — pointer back to this doc
```
