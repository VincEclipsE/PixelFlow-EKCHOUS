#pragma once

// Deterministic seeded RNG. Every random decision in the sim must derive from
// (world_seed, tick_number, pixel_id_or_other_identifier) via xxHash3.
//
// CRITICAL: no Math.random(), no std::mt19937, no glsl-noise. The hash IS the RNG.
// CPU code and GPU shader code use identical hashing (the xxHash3 algorithm is
// fully specified; both sides must produce identical output for identical input).

#include "engine/core/Types.h"

namespace ekchous::determinism {

class SeededRng {
public:
    explicit SeededRng(core::u64 world_seed) noexcept : world_seed_(world_seed) {}

    // Derive a 64-bit random value from (world_seed, tick, identifier).
    core::u64 hash(core::u64 tick, core::u64 identifier) const noexcept;

    // Uniform u32 in [0, max_exclusive).
    core::u32 u32_below(core::u64 tick, core::u64 identifier, core::u32 max_exclusive) const noexcept;

    // Uniform float in [0, 1) — ONLY for non-sim code (UI, sensors, audio).
    // BANNED in sim path; use fixed-point alternatives.
    float unit_float_NONSIM(core::u64 tick, core::u64 identifier) const noexcept;

    core::u64 world_seed() const noexcept { return world_seed_; }

private:
    core::u64 world_seed_;
};

} // namespace ekchous::determinism
