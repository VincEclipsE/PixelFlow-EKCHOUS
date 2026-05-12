#include "engine/sphere/CubedSphere.h"
#include "engine/core/Logger.h"

#include <fstream>
#include <stdexcept>

namespace ekchous::sphere {

// The canonical face-adjacency table.
//
// Conventions:
//   face 0 Top:    +y axis (looking down on north pole)
//   face 1 Bottom: -y axis
//   face 2 North:  +z axis (front)
//   face 3 South:  -z axis (back)
//   face 4 East:   +x axis
//   face 5 West:   -x axis
//
// Edge convention per face (local 2D frame):
//   Top    edge: increasing local y
//   Right  edge: increasing local x at max x
//   Bottom edge: y = 0
//   Left   edge: x = 0
//
// Each row says: leaving (source_face, source_edge), you enter (neighbour_face)
// at (neighbour_edge), then apply (rotation, mirror) to map local coordinates.
//
// The 24 entries are derived from the geometry of an axis-aligned cube.
FaceAdjacency FaceAdjacency::canonical() {
    FaceAdjacency a;
    // Format: {source_face, source_edge, neighbour_face, neighbour_edge, rotation, mirror}
    a.entries_ = {{
        // ----- Top (0) -----
        { 0, /*Top   */ 0, /*North  */ 2, /*Top   */ 0, 2, 0 }, // Top → North: 180°
        { 0, /*Right */ 1, /*East   */ 4, /*Top   */ 0, 3, 0 }, // Top → East: -90°
        { 0, /*Bottom*/ 2, /*South  */ 3, /*Top   */ 0, 0, 0 }, // Top → South
        { 0, /*Left  */ 3, /*West   */ 5, /*Top   */ 0, 1, 0 }, // Top → West: +90°

        // ----- Bottom (1) -----
        { 1, /*Top   */ 0, /*South  */ 3, /*Bottom*/ 2, 0, 0 }, // Bottom → South
        { 1, /*Right */ 1, /*East   */ 4, /*Bottom*/ 2, 1, 0 }, // Bottom → East: +90°
        { 1, /*Bottom*/ 2, /*North  */ 2, /*Bottom*/ 2, 2, 0 }, // Bottom → North: 180°
        { 1, /*Left  */ 3, /*West   */ 5, /*Bottom*/ 2, 3, 0 }, // Bottom → West: -90°

        // ----- North (2) -----
        { 2, /*Top   */ 0, /*Top    */ 0, /*Top   */ 0, 2, 0 }, // North → Top
        { 2, /*Right */ 1, /*East   */ 4, /*Left  */ 3, 0, 0 }, // North → East
        { 2, /*Bottom*/ 2, /*Bottom */ 1, /*Bottom*/ 2, 2, 0 }, // North → Bottom
        { 2, /*Left  */ 3, /*West   */ 5, /*Right */ 1, 0, 0 }, // North → West

        // ----- South (3) -----
        { 3, /*Top   */ 0, /*Top    */ 0, /*Bottom*/ 2, 0, 0 }, // South → Top
        { 3, /*Right */ 1, /*West   */ 5, /*Left  */ 3, 0, 1 }, // South → West (mirrored)
        { 3, /*Bottom*/ 2, /*Bottom */ 1, /*Top   */ 0, 0, 0 }, // South → Bottom
        { 3, /*Left  */ 3, /*East   */ 4, /*Right */ 1, 0, 1 }, // South → East (mirrored)

        // ----- East (4) -----
        { 4, /*Top   */ 0, /*Top    */ 0, /*Right */ 1, 1, 0 }, // East → Top
        { 4, /*Right */ 1, /*South  */ 3, /*Left  */ 3, 0, 1 }, // East → South
        { 4, /*Bottom*/ 2, /*Bottom */ 1, /*Right */ 1, 3, 0 }, // East → Bottom
        { 4, /*Left  */ 3, /*North  */ 2, /*Right */ 1, 0, 0 }, // East → North

        // ----- West (5) -----
        { 5, /*Top   */ 0, /*Top    */ 0, /*Left  */ 3, 3, 0 }, // West → Top
        { 5, /*Right */ 1, /*North  */ 2, /*Left  */ 3, 0, 0 }, // West → North
        { 5, /*Bottom*/ 2, /*Bottom */ 1, /*Left  */ 3, 1, 0 }, // West → Bottom
        { 5, /*Left  */ 3, /*South  */ 3, /*Right */ 1, 0, 1 }, // West → South
    }};
    return a;
}

FaceAdjacency FaceAdjacency::load_from_file(const std::string& path) {
    std::ifstream f(path, std::ios::binary);
    if (!f) {
        throw std::runtime_error("FaceAdjacency::load_from_file: cannot open " + path);
    }
    FaceAdjacency a;
    f.read(reinterpret_cast<char*>(a.entries_.data()),
           sizeof(AdjacencyEntry) * a.entries_.size());
    if (!f) {
        throw std::runtime_error("FaceAdjacency::load_from_file: short read on " + path);
    }
    return a;
}

void FaceAdjacency::save_to_file(const std::string& path) const {
    std::ofstream f(path, std::ios::binary);
    if (!f) {
        throw std::runtime_error("FaceAdjacency::save_to_file: cannot open " + path);
    }
    f.write(reinterpret_cast<const char*>(entries_.data()),
            sizeof(AdjacencyEntry) * entries_.size());
}

SeamCrossing cross_seam(const FaceAdjacency& adj, core::u8 face, Edge edge) noexcept {
    const auto& e = adj.at(face, edge);
    return SeamCrossing{e.neighbour_face, e.neighbour_edge, e.rotation, e.mirror};
}

} // namespace ekchous::sphere
