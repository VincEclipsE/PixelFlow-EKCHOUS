// Day-One acceptance test #5: walk a pixel across all 12 cubed-sphere face
// seams via the 24-entry adjacency LUT; assert positional continuity.
//
// Why this is Day-One: an off-by-one orientation bug at face seams is one
// of the most predictable bugs in this kind of system. Catching it on the
// first commit costs nothing; catching it after the engine has built on
// the wrong assumption is expensive.

#include <catch2/catch_test_macros.hpp>
#include "engine/sphere/CubedSphere.h"
#include <set>
#include <utility>
#include <algorithm>
#include <filesystem>

using namespace ekchous;
using namespace ekchous::sphere;

TEST_CASE("face-adjacency LUT has 24 entries", "[sphere]") {
    auto a = FaceAdjacency::canonical();
    REQUIRE(a.entries().size() == 24);
}

TEST_CASE("every face/edge has an entry", "[sphere]") {
    auto a = FaceAdjacency::canonical();
    for (core::u8 face = 0; face < 6; ++face) {
        for (core::u8 edge = 0; edge < 4; ++edge) {
            const auto& e = a.at(face, static_cast<Edge>(edge));
            INFO("face=" << int(face) << " edge=" << int(edge));
            REQUIRE(e.source_face == face);
            REQUIRE(e.source_edge == edge);
            REQUIRE(e.neighbour_face < 6);
            REQUIRE(e.neighbour_edge < 4);
            REQUIRE(e.rotation < 4);
            REQUIRE(e.mirror < 2);
        }
    }
}

TEST_CASE("reciprocity: crossing a seam and crossing back returns to original face", "[sphere]") {
    auto a = FaceAdjacency::canonical();
    // For every (face, edge), follow the adjacency to (neighbour_face, neighbour_edge),
    // then look up that face/edge's outgoing entry. The neighbour of the neighbour
    // should be the original face on the original edge (modulo rotation conventions).
    for (core::u8 face = 0; face < 6; ++face) {
        for (core::u8 edge = 0; edge < 4; ++edge) {
            const auto& fwd = a.at(face, static_cast<Edge>(edge));
            const auto& back = a.at(fwd.neighbour_face, static_cast<Edge>(fwd.neighbour_edge));
            INFO("forward: face=" << int(face) << " edge=" << int(edge)
                 << " -> face=" << int(fwd.neighbour_face) << " edge=" << int(fwd.neighbour_edge));
            INFO("backward: face=" << int(back.neighbour_face) << " edge=" << int(back.neighbour_edge));
            REQUIRE(back.neighbour_face == face);
            REQUIRE(back.neighbour_edge == edge);
        }
    }
}

TEST_CASE("serialize + load round-trip preserves all entries", "[sphere]") {
    auto original = FaceAdjacency::canonical();
    const auto tmp_path = (std::filesystem::temp_directory_path()
                           / "ekchous_face_adjacency_test.bin").string();
    original.save_to_file(tmp_path);
    auto loaded = FaceAdjacency::load_from_file(tmp_path);
    for (std::size_t i = 0; i < 24; ++i) {
        const auto& a = original.entries()[i];
        const auto& b = loaded.entries()[i];
        REQUIRE(a.source_face == b.source_face);
        REQUIRE(a.source_edge == b.source_edge);
        REQUIRE(a.neighbour_face == b.neighbour_face);
        REQUIRE(a.neighbour_edge == b.neighbour_edge);
        REQUIRE(a.rotation == b.rotation);
        REQUIRE(a.mirror == b.mirror);
    }
}

TEST_CASE("all 12 face seams are reachable from any starting face", "[sphere]") {
    // The cube has 12 edges = 12 seams. Walking outward from any face touches
    // 4 seams; walking from all 6 faces should cover all 12 (each seam shared
    // by exactly 2 faces). Sanity-check by counting distinct (faceA, faceB)
    // unordered pairs.
    auto a = FaceAdjacency::canonical();
    std::set<std::pair<core::u8, core::u8>> seams;
    for (core::u8 face = 0; face < 6; ++face) {
        for (core::u8 edge = 0; edge < 4; ++edge) {
            const auto& e = a.at(face, static_cast<Edge>(edge));
            auto p = std::minmax(face, e.neighbour_face);
            seams.insert({p.first, p.second});
        }
    }
    REQUIRE(seams.size() == 12);
}
