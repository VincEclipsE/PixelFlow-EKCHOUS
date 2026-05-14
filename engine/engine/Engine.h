#pragma once

// Top-level engine for the standalone PixelFlow softbody port.
//
// Responsibilities:
//   - GLFW window + GL 4.3 core context
//   - ImGui boot + per-frame UI
//   - Drive softbody::Physics at the render rate
//   - Render particles (filled circles) + springs (lines) via ImGui draw list
//   - Mouse interaction: drop balls, drag particles

#include "engine/sim/atoms/AtomBond.h"
#include "engine/sim/atoms/AtomElement.h"
#include "engine/sim/atoms/AtomField.h"
#include "engine/sim/atoms/ParticleTemplate.h"
#include "engine/sim/softbody/Filters.h"
#include "engine/sim/softbody/FlowField.h"
#include "engine/sim/softbody/FlowParticles.h"
#include "engine/sim/softbody/Fluid2D.h"
#include "engine/sim/softbody/Physics.h"
#include "engine/sim/softbody/Streamlines.h"
#include <string>
#include <vector>

struct GLFWwindow;

namespace ekchous {

struct EngineConfig {
    int         window_width  = 1280;
    int         window_height = 800;
    std::string window_title  = "PixelFlow Softbody — C++ port";
    bool        headless      = false;
};

class Engine {
public:
    Engine() = default;
    ~Engine();

    bool init(const EngineConfig& cfg);
    int  run();

private:
    bool init_window();
    bool init_imgui();
    bool init_sim();

    void update(float timestep);
    void render_frame();

    // Spawn a SoftBall at world (x, y) using current builder spec.
    void drop_ball(float x, float y);

    // Spawn a SoftGrid (cloth) anchored so that its top edge lands near
    // (x, y). When grid_pin_top_corners_ is true, the top-left and
    // top-right particles are pinned, producing a hanging cloth.
    void drop_cloth(float x, float y);

    // Spawn a SoftRope from (x, y) extending to the right. If
    // rope_pin_first_ is true, the start node is pinned.
    void drop_rope(float x, float y);

    // Spawn a Poisson-disc-sampled blob at (x, y).
    void drop_blob(float x, float y);

    // Spawn a SoftStar at (x, y).
    void drop_star(float x, float y);

    // Spawn a hex-grid softbody at (x, y).
    void drop_hex(float x, float y);

    // Spawn a filled-disc softbody at (x, y).
    void drop_disc(float x, float y);

    // Spawn a free atom at (x, y) from the currently-selected
    // ParticleTemplate. Stamps element_id, radius, mass, enable flags,
    // and user_data onto the new particle.
    void drop_atom(float x, float y);

    // Dispatch the currently-selected RMB action at the given canvas
    // coordinate. Extracted from the click handler so both the initial
    // click and the continuous-hold tick can share one body.
    void fire_rmb_action(float mx, float my);

    // Stamp the active template's per-particle config onto an existing
    // particle (or a list of them). Used by builders so spawned particles
    // carry the template's element_id / template_id / user_data / memory.
    // Mass and radius stay at whatever the builder set (shape-specific).
    void stamp_active_template_on_particle(int p_idx);
    void stamp_active_template_on_indices(const std::vector<int>& indices);

    // Unified spawn: switches on the active template's spawn_pattern and
    // calls the matching builder (passing through template-stamping).
    void spawn_from_template(float mx, float my);

    // Re-type the listed springs to SpringType::Stiff when the active
    // template's default_spring_mode is Stiff. Called by every drop_* after
    // the builder finishes so a "Stiff" template produces rigid-body groups.
    void apply_active_spring_mode_to(const std::vector<int>& spring_indices);

    // Template library I/O. Returns true on success.
    bool save_template_library();
    bool load_template_library();

    // Replace the current scene with a "soft balls in a bowl" demo.
    void preset_bowl();
    // Wind blowing across a hanging cloth.
    void preset_cloth_fan();
    // Tank of soft balls pulled toward a centre attractor.
    void preset_vortex_tank();
    // Kitchen-sink scene exercising every body type + obstacle.
    void preset_chaos();
    // Three orbital attractors + scattered small bodies (zero gravity_y).
    void preset_gravity_wells();
    // Horizontal rope pinned at both ends with a soft ball falling on top.
    void preset_bridge();
    // Vertical stack of soft balls on a floor.
    void preset_tower();

    // Find the nearest particle to (mx, my) within pick_radius_. Returns
    // -1 if none.
    int pick_particle(float mx, float my) const;
    // Find the nearest enabled spring whose segment passes within
    // pick_radius_ of (mx, my). Returns -1 if none.
    int pick_spring(float mx, float my) const;

    EngineConfig cfg_{};
    GLFWwindow*  window_           = nullptr;
    bool         imgui_initialized_ = false;
    double       frame_time_avg_ms_ = 0.0;
    unsigned long long frame_count_ = 0;

    // Time controls.
    bool  paused_          = false;
    bool  step_next_frame_ = false;
    float time_scale_      = 1.0f;

    // F1 help overlay state + frame-time history buffer.
    bool  show_help_                  = false;
    static constexpr int kFrameHistory = 240;
    float frame_history_[kFrameHistory] = {0};
    int   frame_history_idx_          = 0;

    // Live hover readout — stamped during canvas mouse handling, displayed
    // up in the stats block. -1 means nothing under the cursor (or cursor
    // off canvas).
    int   hover_particle_idx_       = -1;
    int   hover_spring_idx_         = -1;
    bool  show_inspector_           = false;
    int   inspector_pinned_particle_ = -1;
    int   inspector_pinned_spring_  = -1;

    softbody::Physics physics_;

    // Canvas (world coords are 1:1 with canvas pixels).
    float canvas_w_ = 1024.0f;
    float canvas_h_ = 720.0f;

    // View transform — applied at render time via the WS()/WSC() helpers in
    // render_frame. The Canvas widget grows with zoom (so the simulation
    // doesn't get cropped) and the Canvas ImGui window provides the natural
    // scroll/pan; middle-mouse drag and mouse wheel are bridged into that
    // scroll/zoom by render_frame.
    float view_zoom_  = 1.0f;
    // Set by HUD "reset view" button; consumed by render_frame to reset the
    // Canvas window's scroll position to (0, 0) on the next frame.
    bool  request_view_reset_scroll_ = false;

    // Render-layer visibility toggles. Particles/springs default on; the
    // "clean default" gives a sim that's visible but with no overlays.
    bool  show_particles_ = true;
    bool  show_springs_   = true;

    // Pixel-grid stamp layer. When on, each tick rasterizes particles into
    // a coarse grid below them: each cell takes the color of the nearest
    // particle whose center falls inside it. Looks like a chunky pixel-art
    // shadow of the simulation.
    bool  pixel_grid_enabled_   = false;
    float pixel_grid_cell_size_ = 8.0f;
    int   pixel_grid_alpha_     = 200;   // 0..255
    std::vector<core::u32> pixel_grid_colors_;
    // Parallel array: template id that "owns" each cell when the owner uses
    // quadrant colors. 0xFFFF = no quadrant owner; render as a single color
    // from pixel_grid_colors_. Otherwise the cell renders as a 2×2 sub-grid
    // pulled from templates_[id].quadrant_colors so the floor mirrors the
    // particle's pie render.
    std::vector<core::u16> pixel_grid_template_;
    int                    pixel_grid_nx_ = 0;
    int                    pixel_grid_ny_ = 0;

    // Mouse drag state.
    int   dragged_particle_idx_ = -1;
    float pick_radius_          = 20.0f;

    // SoftBall builder spec (live-tunable from the HUD).
    float ball_ring_radius_  = 80.0f;
    float ball_node_radius_  = 8.0f;
    int   ball_bend_dist_    = 3;
    float ball_spring_damp_  = 0.9f;

    // SoftGrid (cloth) builder spec.
    int   grid_nodes_x_         = 24;
    int   grid_nodes_y_         = 16;
    float grid_node_radius_     = 6.0f;
    int   grid_bend_dist_       = 3;
    bool  grid_create_struct_   = true;
    bool  grid_create_shear_    = true;
    bool  grid_create_bend_     = true;
    bool  grid_pin_top_corners_ = true;
    float grid_spring_damp_     = 1.0f;

    // SoftRope builder spec.
    int   rope_num_nodes_     = 24;
    float rope_length_        = 400.0f;
    float rope_node_radius_   = 4.0f;
    int   rope_bend_dist_     = 0;
    bool  rope_pin_first_     = true;
    float rope_spring_damp_   = 1.0f;

    // PoissonBlob builder spec.
    float blob_radius_            = 90.0f;
    float blob_node_radius_       = 5.0f;
    float blob_min_node_distance_ = 14.0f;
    int   blob_neighbors_         = 5;
    float blob_spring_damp_       = 1.0f;
    int   blob_seed_              = 0xC0FFEE;

    // SoftStar builder spec.
    int   star_num_points_   = 5;
    float star_outer_radius_ = 80.0f;
    float star_inner_radius_ = 32.0f;
    float star_node_radius_  = 5.0f;
    float star_spring_damp_  = 1.0f;

    // SoftHexGrid builder spec.
    int   hex_nodes_x_      = 14;
    int   hex_nodes_y_      = 10;
    float hex_node_radius_  = 5.0f;
    float hex_spring_damp_  = 1.0f;
    bool  hex_pin_top_row_  = false;

    // SoftDisc builder spec.
    float disc_outer_radius_ = 80.0f;
    float disc_node_radius_  = 5.0f;
    int   disc_num_rings_    = 4;
    int   disc_num_angular_  = 14;
    float disc_spring_damp_  = 1.0f;

    // EKCHOUS particle templates. Replaces the old "atom_element_idx_"
    // palette: each template bundles the full per-particle config and is
    // selectable as the active "kind" for the drop-atom RMB action.
    std::vector<atoms::ParticleTemplate> templates_;
    int                                   selected_template_idx_ = 0;
    char                                  template_name_buf_[64] = "";
    char                                  template_path_buf_[256] = "templates.txt";
    std::string                           template_io_status_;
    // Inspector-side scratch for "save selected as new template" input.
    char                                  inspector_new_tpl_name_[64] = "";

    // EKCHOUS layer 3 — auto-bond formation.
    bool  auto_bond_enabled_      = false;
    int   auto_bond_interval_     = 10;   // ticks between scan passes
    int   auto_bond_tick_counter_ = 0;
    float auto_bond_radius_scale_ = 1.0f;
    int   auto_bond_last_formed_  = 0;

    // (Fickle bond pass removed — friction is now a per-particle property
    //  applied during collision response. See Particle::friction.)

    // EKCHOUS layer 4 — atom radius stamp / data projection.
    atoms::AtomField atom_field_;
    bool  atom_field_enabled_     = false;
    bool  atom_field_show_        = false;
    float atom_field_cell_size_   = 16.0f;
    float atom_field_radius_mul_  = 1.5f;
    float atom_field_alpha_scale_ = 0.55f;

    // Static obstacle builder spec.
    float obs_radius_             = 30.0f;
    float obs_line_thickness_     = 14.0f;

    // Force generator builder spec.
    float fg_pg_strength_       = 0.6f;   // PointGravity strength (negative ⇒ repel)
    float fg_pg_radius_         = 220.0f;
    float fg_drag_half_width_   = 120.0f;
    float fg_drag_half_height_  = 60.0f;
    float fg_drag_amount_       = 0.25f;

    // Render toggles.
    bool  shade_springs_by_tension_ = false;
    bool  color_by_body_            = false;
    bool  color_by_user_data_       = false;
    bool  color_by_element_         = false;
    bool  show_flow_field_          = false;

    // Scene I/O state.
    char        scene_path_buf_[256] = "scene.txt";
    std::string scene_io_status_;

    // Mouse-drag interaction tunables.
    float drag_damp_                = 0.5f;  // move_to drag stiffness (when use_mouse_spring_ off)
    bool  use_mouse_spring_         = false; // true → spring-pull via add_force
    float mouse_spring_stiffness_   = 0.5f;  // Hookean k
    float mouse_spring_damping_     = 0.6f;  // velocity-proportional drag opposing motion

    // Force gun: when on, plain LMB drag becomes a radial push/pull.
    bool  use_force_gun_      = false;
    bool  force_gun_attract_  = false;  // false = push outward, true = pull inward
    float force_gun_strength_ = 0.6f;
    float force_gun_radius_   = 120.0f;

    // Flow field.
    softbody::FlowField flow_field_;
    bool  flow_enabled_       = false;
    float flow_strength_      = 0.05f;   // multiplier when applied as force
    float flow_paint_radius_  = 48.0f;   // world-px radius for shift+LMB stamp
    float flow_paint_scale_   = 0.6f;    // cursor-velocity multiplier
    float flow_preset_strength_ = 1.5f;  // magnitude for preset buttons

    // Previous mouse position in canvas coords (used to compute cursor
    // velocity for flow painting). -1 means "no previous sample yet".
    float prev_mouse_canvas_x_ = -1.0f;
    float prev_mouse_canvas_y_ = -1.0f;

    // RMB action: what right-click does on the canvas.
    //   0 = drop ball
    //   1 = drop static disk obstacle
    //   2 = drag-to-place static line obstacle
    //   3 = drop point gravity (sign from fg_pg_strength_)
    //   4 = drop drag field (sized from fg_drag_half_*)
    //   5 = drop flow-particle emitter
    //   6 = drag-to-create spring between two particles
    //   7 = delete particle (and incident springs)
    //   8 = delete spring under cursor
    //   9 = drop atom (element from atom palette)
    int   rmb_action_idx_         = 0;
    bool  rmb_line_drag_active_   = false;
    float rmb_line_start_x_       = 0.0f;
    float rmb_line_start_y_       = 0.0f;
    bool  rmb_spring_drag_active_ = false;
    int   rmb_spring_start_idx_   = -1;
    // Continuous hold-to-emit: while RMB is held, the selected action fires
    // every 1/rmb_rate_hz_ wall-clock seconds. Drag-mode actions (line, spring)
    // are excluded — they have start/end semantics.
    bool  rmb_continuous_         = false;
    float rmb_rate_hz_            = 10.0f;
    float rmb_accum_time_         = 0.0f;

    // Shift+RMB drag-box selection. Overrides the rmb_action_idx_ dispatch
    // when Shift is held at press time.
    bool  sel_drag_active_     = false;
    float sel_start_x_         = 0.0f;
    float sel_start_y_         = 0.0f;
    std::vector<int>      selected_particles_;
    int                   sel_user_data_value_ = 0;
    float                 sel_impulse_x_       = 0.0f;
    float                 sel_impulse_y_       = -8.0f;  // upward kick by default

    // Flow particles (field-advected dust).
    softbody::FlowParticleSystem flow_particles_;
    bool  fp_auto_emit_        = false;
    int   fp_emit_per_frame_   = 8;
    float fp_emit_vx_jitter_   = 0.6f;
    float fp_emit_vy_jitter_   = 0.6f;
    float fp_damping_          = 0.96f;
    float fp_lifetime_decay_   = 0.02f;
    int   fp_max_count_        = 4096;
    float fp_source_x_         = 0.0f;  // 0 = use canvas centre
    float fp_source_y_         = 0.0f;
    bool  fp_show_trails_      = false;
    // Velocity-based coloring: when on, flow particles ramp through 5 user-
    // pickable color stops over [0, fp_color_max_speed_]. When off, retain
    // the old white→orange ramp.
    bool  fp_velocity_color_   = true;
    float fp_color_max_speed_  = 8.0f;
    // 5 RGB stops for the velocity ramp (blue → cyan → green → yellow → red
    // by default).
    float fp_color_stops_[5][3] = {
        { 0.0f, 0.0f, 1.0f },  // blue
        { 0.0f, 1.0f, 1.0f },  // cyan
        { 0.0f, 1.0f, 0.0f },  // green
        { 1.0f, 1.0f, 0.0f },  // yellow
        { 1.0f, 0.0f, 0.0f },  // red
    };

    // Canvas background color (glClearColor).
    float background_color_[3] = { 0.06f, 0.06f, 0.07f };
    bool  fp_record_trails_    = false;  // gated separately so we don't pay cost when hidden

    // Stam Fluid2D.
    softbody::Fluid2D fluid_;
    bool  fluid_enabled_       = false;
    int   fluid_resolution_    = 64;
    int   fluid_jacobi_iters_  = 40;
    bool  fluid_block_obstacles_ = true;
    float fluid_dt_            = 0.1f;
    float fluid_visc_          = 0.0f;
    float fluid_diff_          = 0.0f;
    float fluid_paint_density_ = 60.0f;   // density per Ctrl+LMB tick
    float fluid_paint_force_   = 6.0f;    // velocity per Ctrl+LMB tick (scaled by cursor delta)
    bool  show_fluid_density_  = true;
    bool  show_fluid_temperature_ = false; // overlay temperature as red+/blue- tint
    bool  fluid_metaball_mode_ = false;   // alt render: overlapping circles instead of cells

    // Render colors for fluid density ramp (low density → high density).
    // Default values match the old hardcoded blue→purple gradient.
    float fluid_color_lo_[3] = { 0.157f, 0.392f, 0.706f };  // (40,100,180)
    float fluid_color_hi_[3] = { 0.8f,   0.6f,   0.9f   };

    // Global default fluid→particle drag coupling strength. Per-template
    // values override this when their fluid coupling link is enabled.
    float fluid_drag_global_strength_ = 0.0f;

    // Fluid post-process: applied to a render-side copy of the density grid
    // each frame. Doesn't affect simulation state. Order is fixed:
    //   cutoff → blur → threshold → bloom → sobel → distance transform.
    bool  fluid_pp_blur_enabled_    = false;
    int   fluid_pp_blur_mode_       = 0;     // 0=gaussian, 1=box, 2=pyramid, 3=SAT
    int   fluid_pp_blur_radius_     = 2;
    int   fluid_pp_blur_pyr_levels_ = 2;
    bool  fluid_pp_normalize_       = false;
    bool  fluid_pp_cutoff_enabled_ = false;
    float fluid_pp_cutoff_         = 5.0f;
    bool  fluid_pp_threshold_enabled_ = false;
    float fluid_pp_threshold_lo_   = 0.0f;
    float fluid_pp_threshold_hi_   = 60.0f;
    bool  fluid_pp_bloom_enabled_  = false;
    float fluid_pp_bloom_threshold_ = 12.0f;
    float fluid_pp_bloom_intensity_ = 1.5f;
    int   fluid_pp_bloom_radius_   = 4;
    bool  fluid_pp_sobel_enabled_  = false;
    bool  fluid_pp_laplace_enabled_ = false;
    bool  fluid_pp_dog_enabled_    = false;
    int   fluid_pp_dog_small_      = 1;
    int   fluid_pp_dog_large_      = 4;
    bool  fluid_pp_median_enabled_ = false;
    int   fluid_pp_median_radius_  = 1;
    bool  fluid_pp_bilateral_enabled_ = false;
    int   fluid_pp_bilateral_radius_  = 2;
    float fluid_pp_bilateral_sigma_space_ = 3.0f;
    float fluid_pp_bilateral_sigma_range_ = 10.0f;
    bool  fluid_pp_dilate_enabled_    = false;
    int   fluid_pp_dilate_radius_     = 1;
    bool  fluid_pp_erode_enabled_     = false;
    int   fluid_pp_erode_radius_      = 1;
    bool  fluid_pp_dt_enabled_     = false;
    float fluid_pp_dt_max_         = 24.0f;
    bool  fluid_pp_multiply_enabled_ = false;
    float fluid_pp_multiply_k_      = 1.0f;
    bool  fluid_pp_gamma_enabled_   = false;
    float fluid_pp_gamma_           = 1.0f;
    bool  fluid_pp_custom_enabled_  = false;
    float fluid_pp_custom_kernel_[9] = {0, 0, 0,  0, 1, 0,  0, 0, 0};  // identity
    bool  fluid_pp_dof_enabled_     = false;
    int   fluid_pp_dof_max_radius_  = 6;
    float fluid_pp_dof_focus_x_     = 512.0f;
    float fluid_pp_dof_focus_y_     = 360.0f;
    float fluid_pp_dof_focus_r_     = 160.0f;
    std::vector<float> fluid_pp_buffer_;  // NxN scratch density buffer

    // Couplings.
    bool  sb_pushes_fluid_     = false;
    float sb_to_fluid_strength_ = 4.0f;   // softbody Verlet vel × this → fluid force
    bool  fluid_pushes_sb_     = false;
    float fluid_to_sb_drag_    = 0.05f;   // drag strength toward fluid velocity
    float fp_fluid_strength_   = 0.0f;    // flow particles' fluid-velocity contribution

    // Streamlines.
    softbody::Streamlines streamlines_;
    bool  show_streamlines_    = false;
    int   stream_cols_         = 24;
    int   stream_rows_         = 16;
    int   stream_steps_        = 32;
    float stream_step_size_    = 4.0f;
    float stream_alpha_        = 0.5f;
};

} // namespace ekchous
