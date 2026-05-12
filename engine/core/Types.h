#pragma once

#include <cstdint>
#include <array>

namespace ekchous::core {

using u8  = std::uint8_t;
using u16 = std::uint16_t;
using u32 = std::uint32_t;
using u64 = std::uint64_t;
using i8  = std::int8_t;
using i16 = std::int16_t;
using i32 = std::int32_t;
using i64 = std::int64_t;
using f32 = float;
using f64 = double;

// Cubed-sphere chunk address.
// face: 0..5 (Top/Bottom/North/South/East/West)
// cx, cy: 20-bit chunk coordinates within a face.
struct ChunkAddress {
    u8  face;
    u32 cx;
    u32 cy;

    bool operator==(const ChunkAddress& o) const noexcept {
        return face == o.face && cx == o.cx && cy == o.cy;
    }

    // Packed 64-bit key for hash table lookup.
    u64 packed() const noexcept {
        return (u64(face) << 56) | (u64(cx) << 28) | u64(cy);
    }
};

// Per-pixel state (2 bits packed in pixel struct).
enum class PixelState : u8 {
    Alive   = 0,
    Dead    = 1,
    Inert   = 2,
    InFlux  = 3,
};

// Per-body integration mode (1 bit packed in BodyEntry).
enum class IntegrationMode : u8 {
    Rigid      = 0,
    Deformable = 1,
};

// Bond directional profile (per-element, indexed in MaterialLUT).
enum class BondProfile : u8 {
    Isotropic = 0,
    Axial     = 1,
    Planar    = 2,
    Cubic     = 3,
    Cleavage  = 4,
};

// Chunk activity flags.
struct ChunkActivity {
    bool active : 1;
    bool chemistry_dirty : 1;
    bool wake_pending : 1;
    bool has_in_flux : 1;
    bool has_alive_body : 1;
    bool _pad : 3;
};
static_assert(sizeof(ChunkActivity) == 1, "ChunkActivity must fit in 1 byte");

} // namespace ekchous::core
