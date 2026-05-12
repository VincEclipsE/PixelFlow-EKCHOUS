# EKCHOUS Engine — Open Questions

Engine-specific risks not yet resolved. Each has a recommended default to unblock Phase 0; final calibration happens during prototype.

Mirrored from `vinceclipse/ekchous@claude/review-game-concept-Tbibx/docs/REVIEW.md §4` and `engine_integration_addendum.md`. Game-design open questions live in the EKCHOUS repo's `EKCHOUS_INDEX.md` open backlog.

## High-leverage open questions

| # | Question | Recommended default | Stress-test trigger |
|---|---|---|---|
| 1 | Can the Stam fluid solver be made deterministic at acceptable cost? | **No — drop it entirely.** Atmosphere = decay-particle field diffusion; vascular = 1D Gauss-Seidel | If atmospheric realism feels insufficient in Phase 2+ |
| 2 | GPU CCL determinism at megafauna scale | **Incremental segmentation**: maintain labels across ticks; only relabel cells with changed bond bitfields. Bound to 8 bodies/tick worst case | Megafauna combat scenario — stress-test in Phase 2 |
| 3 | Charge-graph traversal: ordered BFS deterministic? | Yes if front-set sorted by `pixel_id`, fixed per-tick budget (30 px/tick), `max(amplitude)` conflict resolution. Frontier chunked into fixed-size workgroups | Single-critter nerve-pulse scenario in Phase 1 |
| 4 | Vascular 1D solver cross-vendor stability | Fixed-point pressure (q16.16); integer Gauss-Seidel; float only for readouts | Bleed scenario in Phase 1 |
| 5 | Cubed-sphere face-boundary off-by-one bugs | Offline-generated 24-entry adjacency LUT + unit test walking pixel across all 12 seams Day 1 | Day 1 |
| 6 | 66 ms sim tick budget at 15 Hz — will it fit? | Yes for sub-megafauna scenes (100k–500k active pixels). Megafauna combat triggers **priority shedding**: decay tick, EM-sense, decay-field diffusion drop first | Megafauna combat scenario in Phase 2 |
| 7 | Per-chunk `chemistry_dirty` gating | Yes; most chunks have no chemistry per tick. Set on any bond-form / bond-break / reactive-element-contact event; clear after `ChemistryApply` produces no proposals | Phase 1 |
| 8 | "No sub-pixel resolution" vs. sub-tick motion | Velocity in q8.8 px/tick is sub-tick motion budget, NOT sub-pixel state. Accumulates into integer position over multiple ticks. Documented in `engine_integration_addendum.md §1.1` | Phase 0 |
| 9 | Cloth / rope / webbing taxonomy | Webbing = spider-silk-tendon hybrid; bonded → `alive`/`dead`, NOT `in_flux`. "Verlet cloth" is a deformable body whose bonds are spring-stiff. See `engine_integration_addendum.md §1.4` | Phase 1 |
| 10 | Multiplayer state sync cost | Determinism makes this cheap: `(tick_number, modifier_delta)` transmitted; receiver simulates forward to checksum match. Four-phase hooks: pause-with-timestamp, modifier-push, checksum-handshake, agreed-tick-unpause | Phase 6 |
| 11 | Body / bond / in_flux caps at 8 GB | 256K bodies, 16M bonds, 1M in_flux pixels. Halve for 4 GB | Stress-test in Phase 2 |
| 12 | Build system | CMake 3.24 + ninja, C++20. FetchContent for all deps | Day 1 |
| 13 | Window / input | GLFW for first-pass. Controllers via raw-input layer added later (Phase 2+) | Day 1 |
| 14 | Sim-tick rendering interpolation | NONE for world state (corpus-locked chunky aesthetic). Camera/UI interpolation in render encoder only | Day 1 |
| 15 | Light grid topology vs. decay particle field grid | Share infrastructure (corpus `decay_system §4.10`); 8×8 px per field cell default. 64 field cells per 64×64 chunk fits one workgroup | Phase 1 |
| 16 | When is `imageAtomicAdd` (integer) safe? | Only for counters that don't drive cell-resolution (e.g., proposal-buffer append index). Never for grid-cell content writes | Day 1 |
| 17 | 64-bit integer atomics for fixed-point reductions | `GL_NV_shader_atomic_int64` is vendor-specific. Fallback: 32-bit pair with carry detection | Phase 1 |
| 18 | Transcendental LUT precision | 256-entry quarter-wave for sin/cos; 512-entry for exp/log; linear interpolation in fixed-point. Audit precision in Phase 1 | Phase 1 |
| 19 | Subnormal handling | Explicitly **disable** at boot (flush-to-zero); assert via vendor query. Subnormals add cross-vendor risk for no benefit in this sim | Day 1 |
| 20 | Persistent-mapped buffer ring depth | 3-frame ring matches driver default pipeline depth | Day 1 |
| 21 | RNG seeding | xxHash3 of `(world_seed, tick_number, pixel_id)`. Identical CPU and GPU implementations. No glsl-noise, no `frand` | Day 1 |
| 22 | Element table format | JSON per element under `assets/elements/`. JSON loader at boot, baked into MaterialLUT UBO | Day 1 |
| 23 | Compound recipe format | JSON per recipe under `assets/compounds/`. Loader generates `CompoundRecipes` SSBO at boot | Phase 1 |
| 24 | Tissue profile format | JSON per tissue under `assets/tissues/`. Cross-affinity matrix derived from per-tissue fields | Phase 1 |
| 25 | Save / load checkpoint frequency | Every 30s of sim time + on chunk boundary crossings. Tunable | Phase 5 (save UX) |
| 26 | Time-travel replay scrubbing | Out of scope first-pass. Replay only forward from a snapshot | Phase 5+ |
| 27 | Hot-reload of shaders | Yes for dev builds. Disabled in release | Day 1 |
| 28 | ImGui debug HUD | Yes — frame time per pass, in_flux count, body count, determinism golden-hash trace | Day 1 |
| 29 | Tracy profiler integration | Yes — GPU + CPU markers per pass. Critical for the megafauna combat budget audit | Day 1 |
| 30 | Asset hot-paths | Materials and recipes are read-only after boot. No per-frame asset loading | Day 1 |
| 31 | OpenGL extensions required | Core 4.3 only first-pass. `GL_ARB_compute_shader`, `GL_ARB_shader_storage_buffer_object`, `GL_ARB_shader_image_load_store`. Optional: `GL_KHR_shader_subgroup` for proposal-resolve perf | Day 1 |
| 32 | Vulkan port | Out of scope. Possible Phase 8+ if performance demands | N/A |
| 33 | Mac support | Out of scope. macOS deprecates OpenGL; would need Metal port. Phase 8+ at earliest | N/A |
| 34 | Mobile / WebGL2 | Out of scope. Compute shader requirements rule out WebGL2 | N/A |

## Phase 0 critical path

From the 34 above, the items that must resolve in Phase 0 (Day 0–Day 30):

- #5 face-adjacency LUT + unit test → Day 1
- #12 build system → Day 1
- #13 GLFW window + input → Day 1
- #16 atomic discipline lint → Day 1
- #19 subnormal flush → Day 1
- #21 RNG seeding → Day 1
- #22 element table format → Day 1
- #27 hot-reload shaders → Day 1
- #28 ImGui debug HUD → Day 1
- #29 Tracy profiler → Day 1
- #31 OpenGL extension query → Day 1
- Day-One acceptance: 1000-tick falling-sand replay golden hash identical across same-vendor runs

Everything else is Phase 1+.

## Risks the architecture doesn't yet fully solve

Honest enumeration of where the design might break:

1. **Cross-vendor bit-determinism is best-effort.** Same-vendor is achievable with the integer + fixed-point discipline; cross-vendor depends on the absence of vendor bugs in conforming atomic ops and barrier semantics. The CI matrix (Phase 1+) will surface real divergences; mitigation may require per-vendor shader variants.
2. **Megafauna CCL budget.** Incremental segmentation works when bond-break events are sparse; mass dismemberment (an explosion in a megafauna body) could enqueue thousands of localized relabels and blow the per-tick budget. **Mitigation**: cap relabels per tick; spread across multiple ticks with `stale_segmentation` flag preventing further breaks meanwhile. Not yet stress-tested.
3. **Chemistry combinatorial explosion.** The 5-tier matrix is bounded, but recipe pattern-matching cost rises with active reactive pixels. Per-chunk `chemistry_dirty` gating helps but a chunk full of acid + bone + air would still cost. **Mitigation**: cap proposals per chunk per tick; surplus reactive pairs deferred to next tick.
4. **Permeability + chemistry interactions.** Guest pixels in permeable hosts also bond and react; the chemistry pass must consider host + guest pairs. Spec is sketched but not implemented; risk of unexpected interaction patterns.
5. **Vascular 1D solver instability** at high pressure ratios. Fixed-iteration Gauss-Seidel can oscillate at large gradients. **Mitigation**: pressure clamping at fixed-point bounds; under-relaxation factor (also fixed-point).
6. **Streamer GPU memory pressure** if a player teleports across the planet (no contiguous streaming). The 64K resident chunk cap could be exceeded. **Mitigation**: hard cap with eviction; smooth teleports through a 1–2 second fade-in to allow gradual streaming.
7. **Multiplayer determinism under input divergence.** If two clients have slightly different inputs (latency-induced), they diverge. The 4-phase handoff handles initial sync; in-session sync requires the input log to be authoritatively shared. **Out of Day-One scope**; revisited Phase 6.
8. **Save format evolution.** Format version field exists in chunk serialization, but no migration path defined. First-pass: hard format break on version bump; users lose saves. Acceptable for early access.
