#pragma once

// Single-chunk pixel grid for the Day-One falling-sand demo.
//
// Production layout (per docs/data-layout.md) is a 16-byte packed SSBO with
// element_id, state, integration_mode, mutation, body_membership_flags,
// purity, temperature, charge, damage_accum, decay_progress, bonds,
// grain_orientation, compound_classification_slot, body_id (24-bit), flags.
//
// For Day-One we model just the subset the falling-sand demo needs:
//   - element_id, state, charge (=0 in demo), velocity (only for in_flux)
//
// The full 16-byte struct is documented here so future passes can extend
// without changing the file layout.

#include "engine/core/Types.h"
#include <cstring>
#include <vector>

namespace ekchous::sim {

// Default sim chunk resolution. Corpus tunable range: 64×64 to 256×256.
// The first-pass value of 64 matches the GPU compute workgroup size.
constexpr int kDefaultChunkSize = 64;

// 16-byte packed pixel slot, matching docs/data-layout.md exactly.
// Byte offsets in the spec are unaligned (i16 at offset 3, u16 at offset 5,
// u16 at offset 11), so the struct must be byte-packed across all compilers.
#pragma pack(push, 1)
struct PixelSlot {
    core::u8  element_id;                  // 0
    core::u8  state_flags;                 // 1: state(2) | integration_mode(1) | mutation(1) | body_membership_flags(4)
    core::u8  purity;                      // 2
    core::i16 temperature_q12_4;           // 3..4
    core::u16 charge;                      // 5..6
    core::u8  damage_accum;                // 7
    core::u8  decay_progress;              // 8
    core::u8  bonds_and_grain;             // 9: bonds(5 low bits) | grain(3 high bits) — simplified
    core::u8  compound_slot;               // 10
    core::u16 body_id_low;                 // 11..12
    core::u8  body_id_high;                // 13
    core::u8  flags;                       // 14
    core::u8  color_jitter;                // 15 — repurposed from _pad: render-side hue jitter

    constexpr core::PixelState state() const noexcept {
        return static_cast<core::PixelState>(state_flags & 0b11);
    }
    void set_state(core::PixelState s) noexcept {
        state_flags = (state_flags & ~0b11) | static_cast<core::u8>(s);
    }
};
#pragma pack(pop)
static_assert(sizeof(PixelSlot) == 16, "PixelSlot must be exactly 16 bytes per data-layout.md");

class Chunk {
public:
    explicit Chunk(int size = kDefaultChunkSize)
        : size_(size), pixels_(static_cast<std::size_t>(size) * size) {}

    void clear();

    int size() const noexcept { return size_; }

    PixelSlot& at(int x, int y) noexcept {
        return pixels_[static_cast<std::size_t>(y) * size_ + x];
    }
    const PixelSlot& at(int x, int y) const noexcept {
        return pixels_[static_cast<std::size_t>(y) * size_ + x];
    }

    bool in_bounds(int x, int y) const noexcept {
        return x >= 0 && x < size_ && y >= 0 && y < size_;
    }

    // Raw bytes for golden hashing.
    const core::u8* data() const noexcept {
        return reinterpret_cast<const core::u8*>(pixels_.data());
    }
    std::size_t byte_count() const noexcept {
        return sizeof(PixelSlot) * pixels_.size();
    }

private:
    int size_;
    std::vector<PixelSlot> pixels_;
};

} // namespace ekchous::sim
