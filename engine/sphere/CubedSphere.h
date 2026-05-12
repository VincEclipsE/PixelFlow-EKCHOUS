#pragma once

// Cubed-sphere planet face math.
//
// A planet is six face grids tiling a sphere:
//   face 0 = Top    (north pole quad)
//   face 1 = Bottom (south pole quad)
//   face 2 = North
//   face 3 = South
//   face 4 = East
//   face 5 = West
//
// Chunk address: (face, cx, cy). Local coordinates within a chunk: (lx, ly).
//
// Face seams: an offline-generated 24-entry adjacency LUT specifies for each
// (source_face, edge) the neighbour face + edge + rotation + mirror needed
// to traverse the seam without positional discontinuity.
//
// See docs/architecture.md § "Cubed-sphere face-adjacency LUT".

#include "engine/core/Types.h"
#include <array>
#include <string>

namespace ekchous::sphere {

enum class Edge : core::u8 {
    Top    = 0,
    Right  = 1,
    Bottom = 2,
    Left   = 3,
};

struct AdjacencyEntry {
    core::u8 source_face;
    core::u8 source_edge;
    core::u8 neighbour_face;
    core::u8 neighbour_edge;
    core::u8 rotation; // 0..3 quarter turns
    core::u8 mirror;   // 0 or 1
};
static_assert(sizeof(AdjacencyEntry) == 6, "AdjacencyEntry must be 6 bytes for binary LUT");

class FaceAdjacency {
public:
    static constexpr int kEntryCount = 24; // 6 faces × 4 edges

    // Load the binary LUT from assets/sphere/face_adjacency.bin.
    static FaceAdjacency load_from_file(const std::string& path);

    // Generate the canonical 24-entry table (used by the build-time generator).
    static FaceAdjacency canonical();

    // Serialize to a binary file.
    void save_to_file(const std::string& path) const;

    const AdjacencyEntry& at(core::u8 source_face, Edge edge) const noexcept {
        return entries_[source_face * 4 + static_cast<core::u8>(edge)];
    }

    const std::array<AdjacencyEntry, kEntryCount>& entries() const noexcept { return entries_; }

private:
    std::array<AdjacencyEntry, kEntryCount> entries_{};
};

// Walk a pixel across a face seam, returning the destination face/edge.
// This is the unit-test target: walking a pixel across all 12 face seams must
// produce positional continuity.
struct SeamCrossing {
    core::u8 dest_face;
    core::u8 dest_edge;
    core::u8 rotation;
    core::u8 mirror;
};
SeamCrossing cross_seam(const FaceAdjacency& adj, core::u8 face, Edge edge) noexcept;

} // namespace ekchous::sphere
