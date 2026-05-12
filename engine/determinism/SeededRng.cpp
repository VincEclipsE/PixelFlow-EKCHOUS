#include "engine/determinism/SeededRng.h"

#define XXH_INLINE_ALL
#include <xxhash.h>

namespace ekchous::determinism {

core::u64 SeededRng::hash(core::u64 tick, core::u64 identifier) const noexcept {
    // Pack the triple into a small buffer; xxHash3 over it.
    struct {
        core::u64 seed;
        core::u64 tick;
        core::u64 id;
    } tuple{world_seed_, tick, identifier};
    return XXH3_64bits(&tuple, sizeof(tuple));
}

core::u32 SeededRng::u32_below(core::u64 tick, core::u64 identifier, core::u32 max_exclusive) const noexcept {
    // Deterministic, branch-free, no float math.
    return static_cast<core::u32>(hash(tick, identifier) % core::u64(max_exclusive));
}

float SeededRng::unit_float_NONSIM(core::u64 tick, core::u64 identifier) const noexcept {
    // Use upper 24 bits of the hash to produce [0, 1) in float.
    // This is NON-SIM — only call from rendering / sensor display / audio.
    const core::u64 h = hash(tick, identifier);
    return float(h >> 40) / float(1u << 24);
}

} // namespace ekchous::determinism
