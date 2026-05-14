#include "engine/sim/grid/Chunk.h"

namespace ekchous::sim {

void Chunk::clear() {
    std::memset(pixels_.data(), 0, sizeof(PixelSlot) * pixels_.size());
    // Every pixel starts as inert empty (element_id=0 means "vacuum").
    for (auto& p : pixels_) {
        p.set_state(core::PixelState::Inert);
    }
}

} // namespace ekchous::sim
