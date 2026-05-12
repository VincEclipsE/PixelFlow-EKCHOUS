# EKCHOUS Engine — Data Layout

GPU resource schemas for all sim buffers and textures. Numbers target an 8 GB GPU; halve everything for 4 GB.

## Per-pixel SSBO (16 bytes packed)

One entry per simulated pixel. Two ping-pong buffers `Grid_A` / `Grid_B`. Indexed by `(face, cx, cy, lx, ly)` flattened.

| Field | Width | Offset | Notes |
|---|---|---|---|
| `element_id` | u8 | 0 | Index into 256-entry MaterialLUT (16 first-pass elements + compounds + long tail) |
| `state` | u2 | 1.0 | alive / dead / inert / in_flux (`pixel_physics_foundation §7`) |
| `integration_mode` | u1 | 1.2 | rigid / deformable (drives BodyIntegrate dispatch) |
| `mutation` | u1 | 1.3 | mutation flag (`radiation_and_mutation`) |
| `body_membership_flags` | u4 | 1.4 | wisp-bound, kin, core-adjacent, connection-point |
| `purity` | u8 | 2 | 0–255 (corpus says "low precision fine") |
| `temperature` | s16 q12.4 | 3 | Kelvin, range ±4000K |
| `charge` | u16 | 5 | u16 scaled 0–1; doubles as Luminance proxy on nerve waves |
| `damage_accum` | u8 | 7 | 0–255 |
| `decay_progress` | u8 | 8 | 0.01 increments (`decay_system §2.1`) |
| `bonds` | u8 bitfield | 9 | 8-neighbour bond flags |
| `grain_orientation` | u3 (packed in bonds high bits) | 9 | Drives directional bond profile |
| `compound_classification_slot` | u8 | 10 | 0 = raw element; else compound index |
| `body_id_low` | u16 | 11 | Lower 16 bits of body_id |
| `body_id_high` | u8 | 13 | Upper 8 bits (24-bit body_id total → 16M max) |
| `flags` | u8 | 14 | dirty, queued-for-snap, hover-selected |
| `_pad` | u8 | 15 | reserved |
| **Total** | **16 bytes** | — | — |

**Caps**:
- World grid: 64×64 chunks × ~64K active chunks → ~256M pixels → 4 GB per ping-pong buffer at 16 B/pixel. Reduce by using virtual paging (only resident chunks in GPU memory; see `streaming.md`).
- In_flux active pixels: 1M cap.

## In_flux particle SoA (sparse)

For pixels in `in_flux` state. True SoA — separate buffers per field for cache locality. Cap **1M live in_flux pixels** at 8 GB.

```
struct InFluxParticleSoA {
  vec2_q16_16 pos[N];       // 8 B  — sub-pixel position
  vec2_q8_8   vel[N];       // 4 B  — px/tick velocity
  u32         materialID[N];// 4 B
  u32         bodyID[N];    // 4 B
  u8          flags[N];     // 1 B
  u8          restCounter[N];// 1 B — ticks below rest threshold
  u16         _pad[N];      // 2 B
}; // 24 B/particle → 24 MB at 1M particles
```

Free-list compaction every tick via parallel stream-compact.

## BodyTable SSBO

One entry per `body_id`. Cap **256K bodies** at 8 GB.

```
struct BodyEntry {
  vec2_q16_16 com;            // 8 B
  vec2_q8_8   linear_velocity;// 4 B (RIGID only)
  s16_q8_8    angular_velocity;// 2 B (RIGID only)
  s16_q12_4   orientation;    // 2 B (RIGID only, radians)
  ivec2_q16_16 aabb_min, aabb_max; // 16 B
  u32         total_mass_q16_16;   // 4 B
  u32         angular_inertia_q16_16; // 4 B
  u8          integration_mode;// 1 B  RIGID / DEFORMABLE
  u8          aliveness_flag;  // 1 B  alive / dead
  u16         flags;           // 2 B  wisp_bound, kin, etc.
  u32         wisp_id_or_zero; // 4 B
  u32         core_pixel_id;   // 4 B
  u32         pixel_count;     // 4 B
  u32         pixel_list_head; // 4 B  → BodyPixelPool
}; // 60 B/body → 15 MB at 256K bodies
```

Deformable bodies still maintain COM and AABB for broadphase / streaming / spatial queries. They just don't drive motion off them.

## BodyPixelPool

Flat array of `pixel_id` integers indexed by `(BodyEntry.pixel_list_head, pixel_count)`. Pool grows; bodies are slabs in the pool. Recompacted on body-death.

## Bonds SSBO

Within-body adjacency only. Cross-body interactions are collisions, not bonds. Cap **16M bonds** at 8 GB.

```
struct Bond {
  u32 pixel_a_id;
  u32 pixel_b_id;
  u16 stiffness_q8_8;       // From directional profile + tissue affinity
  u16 connection_point_flag;// 0 = derived from material; 1 = player-tagged junction
}; // 12 B/bond → 192 MB at 16M bonds
```

**Implicit-from-pixel-list optimization** (fallback if memory pressure): bonds for adjacent-cell pairs can be derived from the BodyPixelPool's 4-neighbour scan at runtime. Only non-adjacency bonds (rare — stretched tendons, spider-silk strands) need explicit storage. Switch on demand.

## BondBreakBitset

One bit per bond. Marks bonds broken this tick. Drives the incremental CCL pass.

## ClothParticleSoA, ConstraintSoA, ColorGroupRanges

Verlet cloth/rope/webbing (treated as deformable bodies with axially-strong directional bonds per `engine_integration_addendum.md §1.4`).

```
struct ClothParticleSoA {
  vec2_q16_16 pos[N], prev_pos[N];
  u8          inv_mass[N];
  u32         anchor_body_id[N];
  u32         anchor_local_idx[N];
};

struct ConstraintSoA {
  u32 idx_a, idx_b;
  u16 rest_length_q8_8;
  u16 stiffness_q8_8;
  u8  type; // DISTANCE / BEND / TEAR
};

struct ColorGroupRange {
  u32 offset_start;
  u32 offset_end;
}; // Precomputed graph coloring on CPU; constraint loop dispatches per group serially
```

## ChunkMeta SSBO

One entry per **resident chunk** (64×64 cells = 4096 pixels). Cap **64K resident chunks** at 8 GB.

```
struct ChunkMeta {
  ivec3       address; // (face, cx, cy)
  u8          activity_bits; // active / dormant / wake_pending / chemistry_dirty
  ivec2_q8_8  aabb_min, aabb_max; // tight bounding box of moving content
  u32         in_flux_count;
  u32         body_overlap_count;
  u32         pipe_segment_count;
  u32         nerve_pixel_count;
  u32         dispatch_offset; // into ChunkActiveList
}; // 40 B/chunk → 2.5 MB at 64K chunks
```

## MaterialLUT UBO

256 elements (compounds + long tail in same table). Read-only, std430, uploaded once at boot.

```
struct MaterialEntry { // 64 bytes
  // physical
  float density, hardness, tensile, shear, compression, elasticity;
  float bend_tol, friction, viscosity, surface_tension;
  // thermal
  float conductivity_thermal, thermal_capacity;
  float ignition_temperature, combustion_energy;
  float melt_threshold, vapor_threshold;
  // electrical / EM
  float conductivity_electrical;
  float em_transmissivity;
  // motion transmission
  float kinetic_transmissivity, kinetic_absorption;
  // decay
  u32   decay_tick_interval;
  u8    decay_particle_class;
  // bonds
  u8    bond_directional_profile; // ISOTROPIC / AXIAL / PLANAR / CUBIC / CLEAVAGE
  u8    valence;
  u8    reactivity;
  // permeability (engine_integration_addendum §1.3)
  u8    permeability;
  u8    absorb_capacity;
  u8    diffusion_rate;
  u8    expel_threshold;
  // tissue (for organic)
  u8    tissue_self_affinity;  // (×0.5 to ×4 quantized)
  u8    tissue_cross_affinity; // ditto
  // render
  u32   color_rgba;
  u16   flags;
};
```

256 × 64 B = **16 KB** — fits in one UBO bind.

## CompoundRecipes SSBO

One entry per registered compound recipe (`chemistry.md`). Sparse (~50–100 entries first-pass).

```
struct CompoundRecipe {
  u8  output_element_id;
  u8  input_a_element_id;
  u8  input_b_element_id;
  u8  input_c_element_id; // 0 if not used
  u16 min_temperature_q12_4;
  u16 min_pressure_q16_16;
  u8  bond_pattern_hash; // For pattern-match
  // Output distribution per consumed pixel (sums to 1.0)
  u8  output_a_element_id, output_a_share;
  u8  output_b_element_id, output_b_share;
  u8  output_c_element_id, output_c_share;
  u8  output_d_element_id, output_d_share;
  u32 energy_released_q16_16;
};
```

## Sparse side-buffers (per-chunk, flat sorted arrays)

Allocated lazily; binary-search lookup; stable iteration order.

- `elastic_strain` (q8.8 per pixel) — organic tissue stretch state
- `scent_intensity` (u8) — per-pixel scent contribution to particle field
- `radiation_damage_permanent` (u8) — cumulative, never decays
- `lifetime_damage_healed[]` — per-stress-category combat history (`organic_tissues §4.4`)
- `trace_composition[]` — sparse composition for Cores (Wisp signature derivation)
- `guest_pixels[]` — absorbed guests in permeable host pixels (`engine_integration_addendum §1.3`)

All flat-array, sorted by pixel_id. No hash maps in GPU code.

## Pipe topology CSR

Extracted at chunk-activation time from pipe pixels:

```
struct PipeCSR {
  u32 segment_offsets[N+1]; // CSR row pointers
  u32 pixel_ids[];          // CSR column indices, sorted within each segment
  u16 pressure_q16_16[];    // Per-endpoint pressure
};
```

Deterministic because segment IDs are assigned in stable order at chunk-activate.

## ChargeField (sparse, lazy per-chunk)

`R16F` (or u16 fixed) per cell **only inside chunks that contain nerve tissue**. Outside nerve-bearing chunks the field is implicit zero — no allocation.

## DecayParticleField (sparse field-cell grid)

8×8 px per field cell (corpus `INDEX` Field Cell; REVIEW.md §2 default). 8 particle classes per cell. R16F density per class. Total 16 B per field cell.

At 64×64 chunks → 8×8 field cells per chunk → 64 cells/chunk. At 64K resident chunks → 4M field cells → **64 MB** for the field. Manageable.

## RadiationBulletBuf

Append-only, sorted by spawn tick:

```
struct RadiationBullet {
  vec2_q16_16 pos;
  vec2_q8_8 vel;
  u32 source_pixel_id;
  u32 spawn_tick;
  u16 energy_q8_8;
  u8  particle_class;
  u8  flags;
}; // 24 B
```

## Cubed-sphere face-adjacency LUT (data file)

24 entries packed as:

```
struct FaceAdjacency {
  u8 face;        // source face 0..5
  u8 edge;        // 0=top 1=right 2=bottom 3=left
  u8 neighbour_face;
  u8 neighbour_edge;
  u8 rotation;    // 0/1/2/3 quarter-turns
  u8 mirror;      // 0/1
};
```

6 faces × 4 edges = 24 entries × 6 B = 144 B. Generated offline by a small CMake-time program; checked into `assets/sphere/face_adjacency.bin`. Unit test (`tests/sphere_seam_test.cpp`) walks a pixel across all 12 face seams and asserts positional continuity.

## Memory budget summary (8 GB GPU)

| Resource | Size |
|---|---|
| Grid (ping-pong) — 64K resident chunks × 4096 pixels × 16 B × 2 | 8 GB on paper; **virtual page table caps actual residency at ~512 MB** (see `streaming.md`) |
| In_flux SoA | 24 MB |
| BodyTable | 15 MB |
| BodyPixelPool | 64 MB (sized for 256K bodies × 64 avg pixels) |
| Bonds | 192 MB |
| Cloth + constraints | 50 MB |
| ChunkMeta | 2.5 MB |
| MaterialLUT (UBO) | 16 KB |
| CompoundRecipes | 8 KB |
| Sparse side-buffers | ~100 MB worst case |
| ChargeField (only nerve chunks) | ~50 MB |
| DecayParticleField | 64 MB |
| RadiationBulletBuf | 5 MB |
| Face adjacency LUT | 144 B |
| **Active sim total** | **~1.0 GB** (excluding virtual grid pages) |

Leaves ~5–6 GB for render targets, textures, and headroom. Workable.
