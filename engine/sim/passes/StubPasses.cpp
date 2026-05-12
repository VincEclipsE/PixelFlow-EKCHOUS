// Stubs for the 15 sim passes not wired Day-One.
//
// Each pass has a TODO_PIXELFLOW_PORT pointing at its origin in the
// PixelFlow Java/GLSL library (where applicable), AND a TODO_CORPUS
// pointing at the relevant EKCHOUS design doc section.
//
// These compile cleanly and are intentionally not called from anywhere
// (Day-One Engine only calls FallingSandDemo). The grep audit
// `grep -rn 'TODO_PIXELFLOW_PORT\|TODO_CORPUS' engine/` finds them all.

#include "engine/core/Logger.h"

namespace ekchous::sim::passes {

void chunk_activation_stub() {
    TODO_CORPUS("engine_integration_addendum::1.5");
}

void force_receive_stub() {
    TODO_CORPUS("pixel_physics_foundation::3.4");
}

void bond_propagate_stub() {
    TODO_CORPUS("pixel_physics_foundation::3.4.5");
}

void chemistry_bond_resolve_stub() {
    TODO_CORPUS("chemistry::1.1");
}

void proposal_sort_stub() {
    TODO_CORPUS("determinism::propose-then-resolve");
}

void chemistry_apply_stub() {
    TODO_CORPUS("chemistry::1.2");
}

void charge_propagate_stub() {
    TODO_CORPUS("organic_tissues::2.8");
}

void heat_propagate_stub() {
    TODO_CORPUS("pixel_physics_foundation::3.4.11");
}

void vascular_1d_solve_stub() {
    TODO_CORPUS("organic_tissues::2.9");
}

void radiation_bullets_stub() {
    TODO_CORPUS("radiation_and_mutation");
}

void body_segment_ccl_stub() {
    TODO_PIXELFLOW_PORT("accelerationstructures/DwCollisionGrid.java#labeling()");
}

void body_integrate_deformable_stub() {
    TODO_PIXELFLOW_PORT("softbodydynamics/particle/DwParticle2D.java#update()");
}

void decay_tick_scheduler_stub() {
    TODO_CORPUS("decay_system::2.1");
}

void decay_field_diffuse_stub() {
    TODO_CORPUS("decay_system::4");
}

void em_sense_raycast_stub() {
    TODO_CORPUS("pixel_physics_foundation::8.4");
}

void sleep_promote_stub() {
    TODO_CORPUS("pixel_physics_foundation::11.2");
}

} // namespace ekchous::sim::passes
