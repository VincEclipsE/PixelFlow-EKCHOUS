#pragma once

// Day-One falling-sand demo: deterministic CPU reference implementation.
//
// Wires the Day-One subset of the 18-pass sim pipeline:
//   - Pass 11 (InFluxIntegrate): gravity-only fixed-point integration
//   - Pass 12 (CollisionResolve): snap-to-grid with deterministic tie-break
//   - Pass 14 rigid path:        static stone block (no motion)
//   - Pass 19 (LayeredRender):   one-layer render (CPU → ImGui or texture)
//
// Why CPU-only Day-One: the GPU compute path requires shader compilation,
// glad loader vendoring, and per-vendor testing. The CPU reference proves
// the determinism contract end-to-end with integer-only math. Upgrading
// to GPU compute is Day-N+1 work that ports each pass into a .comp shader.
//
// Integer math throughout:
//   - positions in q16.16 px
//   - velocities in q8.8 px/tick
//   - gravity is a per-tick velocity delta in q8.8
//   - rest-detection by integer compare on velocity magnitude squared
//
// All randomness comes from determinism::SeededRng (xxHash3 over
// (world_seed, tick, identifier)).

#include "engine/core/Types.h"
#include "engine/determinism/FixedPoint.h"
#include "engine/determinism/SeededRng.h"
#include "engine/sim/grid/Chunk.h"
#include <vector>
#include <cstdint>

namespace ekchous::sim {

// One in_flux particle (sand grain currently in motion).
struct InFluxParticle {
    determinism::Vec2Q16_16 pos;
    determinism::Vec2Q8_8   vel;
    core::u8  element_id;
    core::u8  rest_counter; // ticks below rest threshold
    core::u8  flags;
    core::u8  _pad;
    core::u32 particle_id;  // Stable identifier for deterministic tie-break
};
static_assert(sizeof(InFluxParticle) == 24, "InFluxParticle must be 24 bytes");

class FallingSandDemo {
public:
    explicit FallingSandDemo(core::u64 world_seed) noexcept;

    // Restore the demo to a known initial state. Idempotent.
    void reset();

    // Advance one sim tick. Must be deterministic in `tick_index`.
    void tick(core::u64 tick_index);

    // Raw bytes for the golden hash. Includes both grid pixels and in_flux
    // particles, in stable order, so the hash captures all sim state.
    const std::vector<core::u8>& grid_bytes() const noexcept { return hash_buffer_; }

    std::size_t in_flux_count() const noexcept { return in_flux_.size(); }
    std::size_t settled_count() const noexcept { return settled_count_; }

private:
    // Inject the initial scenario: a static stone floor + a stream of sand
    // pixels falling from the top of the chunk. Deterministic from world_seed.
    void inject_scenario();

    // Per-tick passes (CPU equivalents of the GPU compute passes).
    void pass_in_flux_integrate(core::u64 tick_index);
    void pass_collision_resolve(core::u64 tick_index);
    void pass_emit_new_particles(core::u64 tick_index);
    void rebuild_hash_buffer();

    // Constants.
    static constexpr determinism::Q8_8 kGravityPerTick = determinism::Q8_8{30}; // ~0.117 px/tick² — tunable
    static constexpr int kRestThresholdTicks = 8;
    static constexpr core::i64 kRestVelMag2Threshold = 200; // q8.8 squared
    static constexpr core::u8 kElementVacuum = 0;
    static constexpr core::u8 kElementStone  = 1;
    static constexpr core::u8 kElementSand   = 4;

    core::u64 world_seed_;
    determinism::SeededRng rng_;
    Chunk chunk_;
    std::vector<InFluxParticle> in_flux_;
    std::vector<core::u8> hash_buffer_;
    core::u32 next_particle_id_ = 0;
    std::size_t settled_count_ = 0;
};

} // namespace ekchous::sim
