#include "engine/engine/Engine.h"
#include "engine/sim/softbody/FlowParticles.h"
#include "engine/sim/softbody/Obstacles.h"
#include "engine/sim/softbody/PoissonBlob.h"
#include "engine/sim/softbody/SoftBall.h"
#include "engine/sim/softbody/SoftGrid.h"
#include "engine/sim/softbody/SceneIO.h"
#include "engine/sim/softbody/SoftDisc.h"
#include "engine/sim/softbody/SoftHexGrid.h"
#include "engine/sim/softbody/SoftRope.h"
#include "engine/sim/softbody/SoftStar.h"
#include "engine/core/Logger.h"

#include <glad/gl.h>
#include <GLFW/glfw3.h>

#ifndef EKCHOUS_NO_IMGUI
#include <imgui.h>
#include <imgui_impl_glfw.h>
#include <imgui_impl_opengl3.h>
#endif

#include <algorithm>
#include <chrono>
#include <cmath>

namespace ekchous {

namespace {
void glfw_error_callback(int err, const char* msg) {
    LOG_ERROR("GLFW error {}: {}", err, msg);
}

// Map a particle's collision_count to a colour (blue → orange → red).
ImU32 particle_color(int collision_count) {
    if (collision_count <= 0)  return IM_COL32( 80, 140, 230, 255);
    if (collision_count == 1)  return IM_COL32(120, 200, 220, 255);
    if (collision_count == 2)  return IM_COL32(230, 200, 110, 255);
    return                            IM_COL32(240, 110,  80, 255);
}

// Pick a deterministic colour from a fixed palette based on a body id.
// Used when `color_by_body_` is enabled — built-in builders give every
// particle in one soft body the same collision_group, so this colours each
// body uniformly.
ImU32 body_palette_color(int group) {
    static const ImU32 palette[] = {
        IM_COL32(255, 110, 110, 255),  // red
        IM_COL32(110, 200, 255, 255),  // blue
        IM_COL32(150, 230, 130, 255),  // green
        IM_COL32(255, 210,  90, 255),  // yellow
        IM_COL32(230, 130, 230, 255),  // magenta
        IM_COL32(100, 220, 220, 255),  // cyan
        IM_COL32(255, 170,  80, 255),  // orange
        IM_COL32(180, 140, 255, 255),  // purple
    };
    const int n = static_cast<int>(sizeof(palette) / sizeof(palette[0]));
    int idx = group % n;
    if (idx < 0) idx += n;
    return palette[idx];
}

// Standard 2D segment-segment intersection test. Returns true if (p0, p1)
// and (p2, p3) overlap.
bool segments_intersect(float p0x, float p0y, float p1x, float p1y,
                        float p2x, float p2y, float p3x, float p3y) {
    const float s1x = p1x - p0x;
    const float s1y = p1y - p0y;
    const float s2x = p3x - p2x;
    const float s2y = p3y - p2y;
    const float denom = -s2x * s1y + s1x * s2y;
    if (std::fabs(denom) < 1e-9f) return false;
    const float s = (-s1y * (p0x - p2x) + s1x * (p0y - p2y)) / denom;
    const float t = ( s2x * (p0y - p2y) - s2y * (p0x - p2x)) / denom;
    return s >= 0.0f && s <= 1.0f && t >= 0.0f && t <= 1.0f;
}

// Composite tunable widget: a SliderFloat with the value-readout suppressed
// (drag bar only) plus a single-click-editable InputFloat showing the
// current value. Both bound to the same variable so changing one updates
// the other. The label sits to the right.
bool slider_input_float(const char* label, float* v, float v_min, float v_max,
                         const char* fmt = "%.3f") {
    bool changed = false;
    ImGui::PushID(label);
    const float total_w = ImGui::CalcItemWidth();
    const float input_w = 80.0f;
    const float slider_w = total_w - input_w - 4.0f;
    if (slider_w > 16.0f) {
        ImGui::SetNextItemWidth(slider_w);
        if (ImGui::SliderFloat("##slider", v, v_min, v_max, "",
                               ImGuiSliderFlags_AlwaysClamp | ImGuiSliderFlags_NoInput)) {
            changed = true;
        }
        ImGui::SameLine(0, 4);
    }
    ImGui::SetNextItemWidth(input_w);
    if (ImGui::InputFloat("##input", v, 0.0f, 0.0f, fmt)) {
        if (*v < v_min) *v = v_min;
        if (*v > v_max) *v = v_max;
        changed = true;
    }
    ImGui::SameLine(0, 4);
    ImGui::TextUnformatted(label);
    ImGui::PopID();
    return changed;
}

bool slider_input_int(const char* label, int* v, int v_min, int v_max) {
    bool changed = false;
    ImGui::PushID(label);
    const float total_w = ImGui::CalcItemWidth();
    const float input_w = 80.0f;
    const float slider_w = total_w - input_w - 4.0f;
    if (slider_w > 16.0f) {
        ImGui::SetNextItemWidth(slider_w);
        if (ImGui::SliderInt("##slider", v, v_min, v_max, "",
                             ImGuiSliderFlags_AlwaysClamp | ImGuiSliderFlags_NoInput)) {
            changed = true;
        }
        ImGui::SameLine(0, 4);
    }
    ImGui::SetNextItemWidth(input_w);
    if (ImGui::InputInt("##input", v, 0, 0)) {
        if (*v < v_min) *v = v_min;
        if (*v > v_max) *v = v_max;
        changed = true;
    }
    ImGui::SameLine(0, 4);
    ImGui::TextUnformatted(label);
    ImGui::PopID();
    return changed;
}

// Renders the linkage-toggle widgets for a single ParticleTemplate. Reused
// by the template editor in the main HUD and by the inspector when editing
// a focused particle's template inline.
void template_linkage_editor(atoms::ParticleTemplate& t) {
    ImGui::Checkbox("link: fluid coupling (drag from fluid velocity)",
                    &t.link_fluid_coupling);
    if (t.link_fluid_coupling) {
        slider_input_float("fluid drag strength", &t.fluid_drag_strength,
                            0.0f, 5.0f, "%.3f");
    }
    ImGui::Checkbox("link: emit fluid density each tick", &t.link_fluid_emit);
    if (t.link_fluid_emit) {
        slider_input_float("fluid emit amount", &t.fluid_emit_amount,
                            0.0f, 200.0f, "%.1f");
    }
    ImGui::Checkbox("link: emit heat each tick (drives buoyancy)",
                    &t.link_heat_emit);
    if (t.link_heat_emit) {
        slider_input_float("heat emit amount (- to cool)", &t.heat_emit_amount,
                            -60.0f, 60.0f, "%.1f");
    }
    ImGui::Checkbox("link: emit flow particles each tick", &t.link_fp_emit);
    if (t.link_fp_emit) {
        slider_input_int  ("flow particles per tick", &t.fp_emit_per_tick, 0, 32);
        slider_input_float("emit vx jitter", &t.fp_emit_vx_jitter, 0.0f, 5.0f, "%.2f");
        slider_input_float("emit vy jitter", &t.fp_emit_vy_jitter, 0.0f, 5.0f, "%.2f");
    }
}
} // namespace

Engine::~Engine() {
    if (imgui_initialized_) {
        #ifndef EKCHOUS_NO_IMGUI
        ImGui_ImplOpenGL3_Shutdown();
        ImGui_ImplGlfw_Shutdown();
        ImGui::DestroyContext();
        #endif
    }
    if (window_) {
        glfwDestroyWindow(window_);
    }
    glfwTerminate();
}

bool Engine::init(const EngineConfig& cfg) {
    cfg_ = cfg;
    core::Logger::init();
    LOG_INFO("PixelFlow softbody engine starting (headless={})", cfg_.headless);

    if (!init_sim()) return false;

    if (!cfg_.headless) {
        if (!init_window()) return false;
        if (!init_imgui()) return false;
    }
    return true;
}

bool Engine::init_window() {
    glfwSetErrorCallback(glfw_error_callback);
    if (!glfwInit()) {
        LOG_ERROR("glfwInit failed");
        return false;
    }
    // macOS caps OpenGL at 4.1; request 3.3 core there since the runtime
    // path (ImGui draw lists + glDrawArrays) only needs GL 3.3 features.
    // Other platforms get 4.3 in case the dormant gpu/* compute path is
    // wired up later.
#ifdef __APPLE__
    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
#else
    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
#endif
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
    glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);

    window_ = glfwCreateWindow(cfg_.window_width, cfg_.window_height,
                               cfg_.window_title.c_str(), nullptr, nullptr);
    if (!window_) {
        LOG_ERROR("glfwCreateWindow failed");
        glfwTerminate();
        return false;
    }
    glfwMakeContextCurrent(window_);
    glfwSwapInterval(1);

    if (!gladLoadGL(glfwGetProcAddress)) {
        LOG_ERROR("gladLoadGL failed");
        return false;
    }
    const char* gl_ver = reinterpret_cast<const char*>(glGetString(GL_VERSION));
    LOG_INFO("GL initialized: {}", gl_ver ? gl_ver : "<unknown>");
    return true;
}

bool Engine::init_imgui() {
#ifndef EKCHOUS_NO_IMGUI
    IMGUI_CHECKVERSION();
    ImGui::CreateContext();
    ImGuiIO& io = ImGui::GetIO(); (void)io;
    ImGui::StyleColorsDark();
    if (!ImGui_ImplGlfw_InitForOpenGL(window_, true)) {
        LOG_ERROR("ImGui_ImplGlfw_InitForOpenGL failed");
        return false;
    }
    if (!ImGui_ImplOpenGL3_Init("#version 430")) {
        LOG_ERROR("ImGui_ImplOpenGL3_Init failed");
        return false;
    }
    imgui_initialized_ = true;
    return true;
#else
    return true;
#endif
}

bool Engine::init_sim() {
    physics_.params.bounds_xmin = 0.0f;
    physics_.params.bounds_ymin = 0.0f;
    physics_.params.bounds_xmax = canvas_w_;
    physics_.params.bounds_ymax = canvas_h_;
    physics_.params.gravity_y   = 0.6f;
    physics_.particle_damp.bounds    = 0.6f;
    physics_.particle_damp.collision = 0.9f;
    physics_.particle_damp.velocity  = 0.995f;

    // Flow field: ~32px cells across the canvas.
    constexpr float kFlowCell = 32.0f;
    const int fx = static_cast<int>(std::ceil(canvas_w_ / kFlowCell));
    const int fy = static_cast<int>(std::ceil(canvas_h_ / kFlowCell));
    flow_field_.resize(fx, fy, kFlowCell);

    fluid_.resize(fluid_resolution_);
    streamlines_.resize_seeds(stream_cols_, stream_rows_, canvas_w_, canvas_h_);

    // EKCHOUS layer 4: atom-field grid.
    const int af_nx = static_cast<int>(std::ceil(canvas_w_ / atom_field_cell_size_));
    const int af_ny = static_cast<int>(std::ceil(canvas_h_ / atom_field_cell_size_));
    atom_field_.resize(af_nx, af_ny, atom_field_cell_size_);

    // Seed default particle templates if no library file is loadable.
    if (!load_template_library()) {
        templates_.clear();
        for (int i = 0; i < atoms::kNumElements; ++i) {
            atoms::ParticleTemplate t;
            t.name        = std::string(atoms::kElements[i].symbol)
                          + " (" + atoms::kElements[i].name + ")";
            t.element_id  = i;
            t.radius      = atoms::kElements[i].particle_radius;
            t.mass        = atoms::kElements[i].mass;
            templates_.push_back(t);
        }
        selected_template_idx_ = 0;
    }

    // Default emitter at canvas centre so flow particles spawn out of the box.
    softbody::FlowEmitter default_em;
    default_em.x = canvas_w_ * 0.5f;
    default_em.y = canvas_h_ * 0.5f;
    default_em.per_frame = fp_emit_per_frame_;
    default_em.vx_jitter = fp_emit_vx_jitter_;
    default_em.vy_jitter = fp_emit_vy_jitter_;
    flow_particles_.add_emitter(default_em);

    // Seed with a single ball so there's something on screen at boot.
    drop_ball(canvas_w_ * 0.5f, canvas_h_ * 0.25f);
    return true;
}

int Engine::run() {
    if (cfg_.headless) {
        // Headless: drive a few hundred ticks then exit cleanly.
        for (int i = 0; i < 600; ++i) update(1.0f);
        LOG_INFO("Headless run complete ({} particles, {} springs).",
                 physics_.particles().size(), physics_.springs().size());
        return 0;
    }

    while (!glfwWindowShouldClose(window_)) {
        const auto t0 = std::chrono::high_resolution_clock::now();

        glfwPollEvents();
        if (!paused_ || step_next_frame_) {
            update(time_scale_);
            step_next_frame_ = false;
        }
        render_frame();
        glfwSwapBuffers(window_);

        const auto t1 = std::chrono::high_resolution_clock::now();
        const double ms = std::chrono::duration<double, std::milli>(t1 - t0).count();
        // EMA so the readout doesn't jitter.
        frame_time_avg_ms_ = 0.9 * frame_time_avg_ms_ + 0.1 * ms;
        // Sparkline ring buffer takes the raw per-frame ms.
        frame_history_[frame_history_idx_] = static_cast<float>(ms);
        frame_history_idx_ = (frame_history_idx_ + 1) % kFrameHistory;
        ++frame_count_;
    }
    return 0;
}

void Engine::update(float timestep) {
    if (flow_enabled_) {
        flow_field_.apply_to(physics_.particles(), flow_strength_);
    }

    // Coupling A: softbody → fluid. Each softbody particle's implicit Verlet
    // velocity is stamped into the fluid as a force at its cell. Must run
    // BEFORE fluid_.step() so the sources are picked up.
    if (sb_pushes_fluid_ && fluid_enabled_) {
        for (const auto& p : physics_.particles()) {
            const float vx = (p.cx - p.px) * sb_to_fluid_strength_;
            const float vy = (p.cy - p.py) * sb_to_fluid_strength_;
            fluid_.add_force_at(p.cx, p.cy, canvas_w_, canvas_h_, vx, vy);
        }
    }

    physics_.update(timestep);

    if (fluid_enabled_) {
        fluid_.iterations = fluid_jacobi_iters_;
        // Build the obstacle mask each frame from current static obstacles.
        if (fluid_block_obstacles_ &&
            (!physics_.static_disks().empty() ||
             !physics_.static_boxes().empty() ||
             !physics_.static_lines().empty())) {
            const int n = fluid_.n();
            const std::size_t size = static_cast<std::size_t>(n + 2) * (n + 2);
            std::vector<core::u8> mask(size, 0);
            for (int j = 1; j <= n; ++j) {
                for (int i = 1; i <= n; ++i) {
                    const float wx = (i - 0.5f) / n * canvas_w_;
                    const float wy = (j - 0.5f) / n * canvas_h_;
                    bool blocked = false;
                    for (const auto& d : physics_.static_disks()) {
                        const float dx = wx - d.disk.cx;
                        const float dy = wy - d.disk.cy;
                        if (dx*dx + dy*dy <= d.disk.radius * d.disk.radius) {
                            blocked = true; break;
                        }
                    }
                    if (!blocked) {
                        for (const auto& b : physics_.static_boxes()) {
                            if (wx >= b.aabb.min_x && wx <= b.aabb.max_x &&
                                wy >= b.aabb.min_y && wy <= b.aabb.max_y) {
                                blocked = true; break;
                            }
                        }
                    }
                    if (!blocked) {
                        for (const auto& l : physics_.static_lines()) {
                            const float vx = l.bx - l.ax;
                            const float vy = l.by - l.ay;
                            const float wxa = wx - l.ax;
                            const float wya = wy - l.ay;
                            const float len2 = vx*vx + vy*vy;
                            if (len2 < 1e-6f) continue;
                            float t = (wxa*vx + wya*vy) / len2;
                            if (t < 0) t = 0; else if (t > 1) t = 1;
                            const float cx = l.ax + vx * t;
                            const float cy = l.ay + vy * t;
                            const float dx = wx - cx;
                            const float dy = wy - cy;
                            if (dx*dx + dy*dy <= l.thickness * l.thickness) {
                                blocked = true; break;
                            }
                        }
                    }
                    if (blocked) {
                        mask[static_cast<std::size_t>(i) +
                             static_cast<std::size_t>(n + 2) * j] = 1;
                    }
                }
            }
            fluid_.set_obstacles(mask);
        } else {
            fluid_.clear_obstacles();
        }
        fluid_.step(fluid_dt_, fluid_visc_, fluid_diff_);
    }

    // Coupling B: fluid → softbody. Drag toward fluid velocity for every
    // non-pinned particle. Runs after fluid step so we read the freshest field.
    if (fluid_pushes_sb_ && fluid_enabled_) {
        for (auto& p : physics_.particles()) {
            if (!p.enable_forces) continue;
            float fu, fv;
            fluid_.sample_velocity(p.cx, p.cy, canvas_w_, canvas_h_, fu, fv);
            const float vx = p.cx - p.px;
            const float vy = p.cy - p.py;
            p.add_force((fu - vx) * fluid_to_sb_drag_,
                        (fv - vy) * fluid_to_sb_drag_);
        }
    }

    // Flow particles: each frame, fire every enabled FlowEmitter in the list.
    // Capped by fp_max_count_. Disable globally with fp_auto_emit_.
    if (fp_auto_emit_) {
        flow_particles_.emit_from_emitters(fp_max_count_);
    }
    fp_record_trails_ = fp_show_trails_;
    flow_particles_.update(flow_field_, timestep, fp_damping_, fp_lifetime_decay_,
                           0.0f, 0.0f, canvas_w_, canvas_h_,
                           fluid_enabled_ ? &fluid_ : nullptr, fp_fluid_strength_,
                           canvas_w_, canvas_h_,
                           fp_record_trails_);

    // Streamlines: re-trace each frame from current fluid state.
    if (show_streamlines_ && fluid_enabled_) {
        streamlines_.update(fluid_, stream_steps_, stream_step_size_,
                            canvas_w_, canvas_h_);
    }

    // EKCHOUS layer 3: auto-bond formation. Run periodically (not every
    // frame) so the O(N·k) scan amortizes cheaply on large scenes.
    if (auto_bond_enabled_) {
        ++auto_bond_tick_counter_;
        if (auto_bond_tick_counter_ >= auto_bond_interval_) {
            auto_bond_last_formed_ =
                atoms::try_auto_bonds(physics_, auto_bond_radius_scale_);
            auto_bond_tick_counter_ = 0;
        }
    }

    // EKCHOUS layer 4: rebuild the atom-field stamp from current positions.
    if (atom_field_enabled_) {
        atom_field_.rebuild(physics_.particles(), atom_field_radius_mul_);
    }

    // Per-template tool linkages — single sweep over particles. Reads each
    // particle's template_id and dispatches based on which links are
    // enabled. Parameter links (fluid coupling) modulate forces; emitter
    // links (fluid/heat/fp emit) inject into the linked tool. Default
    // template state has all emitters off so opt-in only.
    if (!templates_.empty()) {
        const std::size_t n_tpl = templates_.size();
        const float rho = fluid_.mass_density;
        for (auto& p : physics_.particles()) {
            const std::size_t tid = p.template_id;
            if (tid >= n_tpl) continue;
            const auto& t = templates_[tid];

            // Fluid coupling: pure parameter — fluid drag onto particle.
            if (fluid_enabled_ && fluid_drag_global_strength_ > 0.0f) {
                const float k = t.link_fluid_coupling
                                    ? t.fluid_drag_strength
                                    : fluid_drag_global_strength_;
                if (k > 0.0f) {
                    float fu, fv;
                    fluid_.sample_velocity(p.cx, p.cy, canvas_w_, canvas_h_, fu, fv);
                    p.px -= fu * k * rho;
                    p.py -= fv * k * rho;
                }
            }
            // Emitter link: fluid density.
            if (t.link_fluid_emit && fluid_enabled_) {
                fluid_.add_density_at(p.cx, p.cy, canvas_w_, canvas_h_,
                                       t.fluid_emit_amount);
            }
            // Emitter link: heat.
            if (t.link_heat_emit && fluid_enabled_) {
                fluid_.add_heat_at(p.cx, p.cy, canvas_w_, canvas_h_,
                                    t.heat_emit_amount);
            }
            // Emitter link: flow particles.
            if (t.link_fp_emit && t.fp_emit_per_tick > 0) {
                flow_particles_.emit(p.cx, p.cy, t.fp_emit_per_tick,
                                     t.fp_emit_vx_jitter, t.fp_emit_vy_jitter,
                                     fp_max_count_);
            }
        }
    }
}

void Engine::drop_ball(float x, float y) {
    softbody::SoftBallSpec spec;
    spec.center_x         = x;
    spec.center_y         = y;
    spec.ring_radius      = ball_ring_radius_;
    spec.node_radius      = ball_node_radius_;
    spec.bend_spring_dist = ball_bend_dist_;
    spec.spring_damp_inc  = ball_spring_damp_;
    spec.spring_damp_dec  = ball_spring_damp_;
    softbody::build_soft_ball(physics_, spec);
}

void Engine::drop_rope(float x, float y) {
    softbody::SoftRopeSpec spec;
    spec.start_x          = x;
    spec.start_y          = y;
    spec.end_x            = x + rope_length_;
    spec.end_y            = y;
    spec.num_nodes        = rope_num_nodes_;
    spec.node_radius      = rope_node_radius_;
    spec.bend_spring_dist = rope_bend_dist_;
    spec.spring_damp_inc  = rope_spring_damp_;
    spec.spring_damp_dec  = rope_spring_damp_;
    if (rope_pin_first_) spec.pinned.push_back(0);
    softbody::build_soft_rope(physics_, spec);
}

void Engine::preset_bowl() {
    physics_.reset();
    const float cw = canvas_w_;
    const float ch = canvas_h_;
    const float thickness = 12.0f;
    // Bottom + diagonal walls.
    physics_.add_static_line(cw * 0.18f, ch * 0.85f, cw * 0.82f, ch * 0.85f, thickness);
    physics_.add_static_line(cw * 0.08f, ch * 0.45f, cw * 0.18f, ch * 0.85f, thickness);
    physics_.add_static_line(cw * 0.92f, ch * 0.45f, cw * 0.82f, ch * 0.85f, thickness);

    // Drop a few staggered SoftBalls into the bowl.
    for (int i = 0; i < 6; ++i) {
        softbody::SoftBallSpec spec;
        spec.center_x         = cw * (0.30f + (i % 3) * 0.20f);
        spec.center_y         = ch * (0.10f + (i / 3) * 0.10f);
        spec.ring_radius      = 40.0f;
        spec.node_radius      = 6.0f;
        spec.bend_spring_dist = ball_bend_dist_;
        spec.spring_damp_inc  = ball_spring_damp_;
        spec.spring_damp_dec  = ball_spring_damp_;
        softbody::build_soft_ball(physics_, spec);
    }
}

void Engine::preset_cloth_fan() {
    physics_.reset();
    // Hanging cloth pinned at top corners.
    drop_cloth(canvas_w_ * 0.6f, canvas_h_ * 0.12f);
    // Wind blowing right across the canvas.
    flow_field_.make_wind(2.0f, 0.0f);
    flow_enabled_   = true;
    flow_strength_  = 0.06f;
    show_flow_field_ = true;
}

void Engine::preset_bridge() {
    physics_.reset();
    softbody::SoftRopeSpec rope;
    rope.start_x          = canvas_w_ * 0.18f;
    rope.start_y          = canvas_h_ * 0.40f;
    rope.end_x            = canvas_w_ * 0.82f;
    rope.end_y            = canvas_h_ * 0.40f;
    rope.num_nodes        = 32;
    rope.node_radius      = 5.0f;
    rope.bend_spring_dist = 2;  // stiffen so the bridge doesn't deflate too easily
    rope.pinned.push_back(0);
    rope.pinned.push_back(rope.num_nodes - 1);
    softbody::build_soft_rope(physics_, rope);
    // Ball drops on the middle to test the bridge.
    softbody::SoftBallSpec ball;
    ball.center_x         = canvas_w_ * 0.5f;
    ball.center_y         = canvas_h_ * 0.12f;
    ball.ring_radius      = 38.0f;
    ball.node_radius      = 6.0f;
    ball.bend_spring_dist = 3;
    softbody::build_soft_ball(physics_, ball);
}

void Engine::preset_tower() {
    physics_.reset();
    physics_.add_static_line(20.0f,             canvas_h_ - 20.0f,
                              canvas_w_ - 20.0f, canvas_h_ - 20.0f, 14.0f);
    for (int i = 0; i < 8; ++i) {
        softbody::SoftBallSpec spec;
        spec.center_x         = canvas_w_ * 0.5f;
        spec.center_y         = canvas_h_ - 60.0f - i * 70.0f;
        spec.ring_radius      = 30.0f;
        spec.node_radius      = 5.0f;
        spec.bend_spring_dist = 2;
        softbody::build_soft_ball(physics_, spec);
    }
}

void Engine::preset_gravity_wells() {
    physics_.reset();
    physics_.params.gravity_y = 0.0f;   // Disable down-pull so orbits don't decay too fast.
    physics_.params.gravity_x = 0.0f;
    // Three attractors triangle-arranged across the canvas.
    physics_.add_point_gravity({canvas_w_ * 0.25f, canvas_h_ * 0.40f, 0.5f, 240.0f});
    physics_.add_point_gravity({canvas_w_ * 0.75f, canvas_h_ * 0.40f, 0.5f, 240.0f});
    physics_.add_point_gravity({canvas_w_ * 0.50f, canvas_h_ * 0.75f, 0.5f, 240.0f});
    // Scatter ten small SoftBalls so collisions + orbital pulls stir them up.
    for (int i = 0; i < 10; ++i) {
        softbody::SoftBallSpec spec;
        spec.center_x         = canvas_w_ * (0.10f + (i % 5) * 0.18f);
        spec.center_y         = canvas_h_ * (0.10f + (i / 5) * 0.12f);
        spec.ring_radius      = 22.0f;
        spec.node_radius      = 4.0f;
        spec.bend_spring_dist = 2;
        softbody::build_soft_ball(physics_, spec);
    }
}

void Engine::preset_chaos() {
    physics_.reset();
    const float cw = canvas_w_;
    const float ch = canvas_h_;
    const float t  = 12.0f;
    // Box bounding the scene (matches canvas inset so falling stops cleanly).
    physics_.add_static_line(t, ch - t, cw - t, ch - t, t);
    physics_.add_static_line(t, t,      t,      ch - t, t);
    physics_.add_static_line(cw - t, t, cw - t, ch - t, t);
    // Centre island.
    physics_.add_static_box(cw * 0.5f - 80.0f, ch * 0.55f - 24.0f,
                             cw * 0.5f + 80.0f, ch * 0.55f + 24.0f);
    // 4 SoftBalls dropped along the top.
    for (int i = 0; i < 4; ++i) {
        softbody::SoftBallSpec spec;
        spec.center_x         = cw * (0.20f + i * 0.20f);
        spec.center_y         = ch * 0.12f;
        spec.ring_radius      = 36.0f;
        spec.node_radius      = 5.0f;
        spec.bend_spring_dist = 2;
        softbody::build_soft_ball(physics_, spec);
    }
    // A hanging pinned rope on the right.
    softbody::SoftRopeSpec rope;
    rope.start_x          = cw * 0.85f;
    rope.start_y          = ch * 0.10f;
    rope.end_x            = cw * 0.85f;
    rope.end_y            = ch * 0.40f;
    rope.num_nodes        = 20;
    rope.node_radius      = 4.0f;
    rope.pinned.push_back(0);
    softbody::build_soft_rope(physics_, rope);
    // A PoissonBlob on the left.
    softbody::PoissonBlobSpec blob;
    blob.center_x          = cw * 0.18f;
    blob.center_y          = ch * 0.38f;
    blob.radius            = 70.0f;
    blob.node_radius       = 5.0f;
    blob.min_node_distance = 14.0f;
    softbody::build_poisson_blob(physics_, blob);
    // A star bouncing in the middle.
    softbody::SoftStarSpec star;
    star.center_x = cw * 0.50f;
    star.center_y = ch * 0.30f;
    star.outer_radius = 50.0f;
    star.inner_radius = 22.0f;
    star.num_points   = 6;
    softbody::build_soft_star(physics_, star);
}

void Engine::preset_vortex_tank() {
    physics_.reset();
    const float cw = canvas_w_;
    const float ch = canvas_h_;
    const float thickness = 14.0f;
    // Tank — 3 walls forming a rectangular pit, top open.
    physics_.add_static_line(cw * 0.10f, ch * 0.30f, cw * 0.10f, ch * 0.90f, thickness);
    physics_.add_static_line(cw * 0.90f, ch * 0.30f, cw * 0.90f, ch * 0.90f, thickness);
    physics_.add_static_line(cw * 0.10f, ch * 0.90f, cw * 0.90f, ch * 0.90f, thickness);
    // Centre attractor.
    softbody::PointGravity g{cw * 0.5f, ch * 0.6f, 0.8f, 280.0f};
    physics_.add_point_gravity(g);
    // Scatter 8 SoftBalls around the tank.
    for (int i = 0; i < 8; ++i) {
        softbody::SoftBallSpec spec;
        spec.center_x         = cw * (0.20f + (i % 4) * 0.20f);
        spec.center_y         = ch * (0.40f + (i / 4) * 0.10f);
        spec.ring_radius      = 30.0f;
        spec.node_radius      = 5.0f;
        spec.bend_spring_dist = 2;
        softbody::build_soft_ball(physics_, spec);
    }
}

void Engine::drop_blob(float x, float y) {
    softbody::PoissonBlobSpec spec;
    spec.center_x           = x;
    spec.center_y           = y;
    spec.radius             = blob_radius_;
    spec.node_radius        = blob_node_radius_;
    spec.min_node_distance  = blob_min_node_distance_;
    spec.neighbors_per_node = blob_neighbors_;
    spec.spring_damp_inc    = blob_spring_damp_;
    spec.spring_damp_dec    = blob_spring_damp_;
    spec.seed               = static_cast<std::uint32_t>(blob_seed_);
    softbody::build_poisson_blob(physics_, spec);
    // Re-seed for the next click so each blob is unique.
    ++blob_seed_;
}

void Engine::drop_star(float x, float y) {
    softbody::SoftStarSpec spec;
    spec.center_x        = x;
    spec.center_y        = y;
    spec.num_points      = star_num_points_;
    spec.outer_radius    = star_outer_radius_;
    spec.inner_radius    = star_inner_radius_;
    spec.node_radius     = star_node_radius_;
    spec.spring_damp_inc = star_spring_damp_;
    spec.spring_damp_dec = star_spring_damp_;
    softbody::build_soft_star(physics_, spec);
}

void Engine::drop_atom(float x, float y) {
    if (selected_template_idx_ < 0 ||
        selected_template_idx_ >= static_cast<int>(templates_.size())) return;
    const auto& t = templates_[selected_template_idx_];
    const int idx = physics_.add_particle(x, y, t.radius);
    auto& p = physics_.particles()[idx];
    p.element_id        = static_cast<core::u8>(t.element_id);
    p.template_id       = static_cast<core::u8>(selected_template_idx_);
    p.mass              = t.mass;
    p.enable_forces     = t.enable_forces;
    p.enable_springs    = t.enable_springs;
    p.enable_collisions = t.enable_collisions;
    p.user_data         = t.user_data;
}

void Engine::fire_rmb_action(float mx, float my) {
    switch (rmb_action_idx_) {
        case 0:
            drop_ball(mx, my);
            break;
        case 1:
            physics_.add_static_disk(mx, my, obs_radius_);
            break;
        case 2:
            rmb_line_drag_active_ = true;
            rmb_line_start_x_ = mx;
            rmb_line_start_y_ = my;
            break;
        case 3: {
            softbody::PointGravity g{mx, my, fg_pg_strength_, fg_pg_radius_};
            physics_.add_point_gravity(g);
            break;
        }
        case 4: {
            softbody::DragField f;
            f.aabb = softbody::AABB{
                mx - fg_drag_half_width_,  my - fg_drag_half_height_,
                mx + fg_drag_half_width_,  my + fg_drag_half_height_};
            f.drag = fg_drag_amount_;
            physics_.add_drag_field(f);
            break;
        }
        case 5: {
            softbody::FlowEmitter e;
            e.x = mx;
            e.y = my;
            e.per_frame = fp_emit_per_frame_;
            e.vx_jitter = fp_emit_vx_jitter_;
            e.vy_jitter = fp_emit_vy_jitter_;
            flow_particles_.add_emitter(e);
            break;
        }
        case 6:
            rmb_spring_drag_active_ = true;
            rmb_spring_start_idx_ = pick_particle(mx, my);
            break;
        case 7: {
            const int pidx = pick_particle(mx, my);
            if (pidx >= 0) physics_.remove_particle(pidx);
            if (dragged_particle_idx_ >= pidx) dragged_particle_idx_ = -1;
            break;
        }
        case 8: {
            const int sidx = pick_spring(mx, my);
            if (sidx >= 0) {
                auto& sps = physics_.springs();
                sps.erase(sps.begin() + sidx);
            }
            break;
        }
        case 9:
            drop_atom(mx, my);
            break;
        default: break;
    }
}

bool Engine::save_template_library() {
    std::ofstream out(template_path_buf_);
    if (!out) return false;
    out << "# PixelFlow particle template library\n";
    out << "version 1\n";
    for (const auto& t : templates_) {
        out << "tpl " << t.element_id << " " << t.radius << " " << t.mass << " "
            << (t.enable_forces     ? 1 : 0) << " "
            << (t.enable_springs    ? 1 : 0) << " "
            << (t.enable_collisions ? 1 : 0) << " "
            << t.user_data << " "
            << t.name << "\n";
    }
    return out.good();
}

bool Engine::load_template_library() {
    std::ifstream in(template_path_buf_);
    if (!in) return false;
    templates_.clear();
    std::string line;
    while (std::getline(in, line)) {
        if (line.empty() || line[0] == '#') continue;
        std::istringstream iss(line);
        std::string cmd;
        if (!(iss >> cmd)) continue;
        if (cmd != "tpl") continue;
        atoms::ParticleTemplate t;
        int ef = 1, es = 1, ec = 1;
        unsigned int ud = 0;
        if (!(iss >> t.element_id >> t.radius >> t.mass >> ef >> es >> ec >> ud)) continue;
        t.enable_forces     = (ef != 0);
        t.enable_springs    = (es != 0);
        t.enable_collisions = (ec != 0);
        t.user_data         = ud;
        // Name = everything after the integers, to end of line.
        std::string rest;
        std::getline(iss >> std::ws, rest);
        if (rest.empty()) rest = "(unnamed)";
        t.name = rest;
        templates_.push_back(t);
    }
    if (templates_.empty()) return false;
    if (selected_template_idx_ >= static_cast<int>(templates_.size())) {
        selected_template_idx_ = 0;
    }
    return true;
}

void Engine::drop_disc(float x, float y) {
    softbody::SoftDiscSpec spec;
    spec.center_x        = x;
    spec.center_y        = y;
    spec.outer_radius    = disc_outer_radius_;
    spec.node_radius     = disc_node_radius_;
    spec.num_rings       = disc_num_rings_;
    spec.num_angular     = disc_num_angular_;
    spec.spring_damp_inc = disc_spring_damp_;
    spec.spring_damp_dec = disc_spring_damp_;
    softbody::build_soft_disc(physics_, spec);
}

void Engine::drop_hex(float x, float y) {
    softbody::SoftHexGridSpec spec;
    // Centre the grid on (x, y) by offsetting start to top-left.
    const float w = (hex_nodes_x_ - 1) * hex_node_radius_ * 2.0f;
    const float h = (hex_nodes_y_ - 1) * hex_node_radius_ * 1.732f;
    spec.start_x         = x - w * 0.5f;
    spec.start_y         = y - h * 0.5f;
    spec.nodes_x         = hex_nodes_x_;
    spec.nodes_y         = hex_nodes_y_;
    spec.node_radius     = hex_node_radius_;
    spec.spring_damp_inc = hex_spring_damp_;
    spec.spring_damp_dec = hex_spring_damp_;
    if (hex_pin_top_row_) {
        for (int c = 0; c < hex_nodes_x_; ++c) spec.pinned.push_back({c, 0});
    }
    softbody::build_soft_hex_grid(physics_, spec);
}

void Engine::drop_cloth(float x, float y) {
    softbody::SoftGridSpec spec;
    // Centre the cloth horizontally on x; top edge lands at y.
    const float w = (grid_nodes_x_ - 1) * grid_node_radius_ * 2.0f;
    spec.start_x          = x - w * 0.5f;
    spec.start_y          = y;
    spec.nodes_x          = grid_nodes_x_;
    spec.nodes_y          = grid_nodes_y_;
    spec.node_radius      = grid_node_radius_;
    spec.bend_spring_dist = grid_bend_dist_;
    spec.create_struct    = grid_create_struct_;
    spec.create_shear     = grid_create_shear_;
    spec.create_bend      = grid_create_bend_;
    spec.spring_damp_inc  = grid_spring_damp_;
    spec.spring_damp_dec  = grid_spring_damp_;
    if (grid_pin_top_corners_) {
        spec.pinned.push_back({0,                grid_nodes_y_ > 0 ? 0 : 0});
        spec.pinned.push_back({grid_nodes_x_ - 1, 0});
    }
    softbody::build_soft_grid(physics_, spec);
}

int Engine::pick_particle(float mx, float my) const {
    int best = -1;
    float best_d2 = pick_radius_ * pick_radius_;
    const auto& parts = physics_.particles();
    for (std::size_t i = 0; i < parts.size(); ++i) {
        const float dx = parts[i].cx - mx;
        const float dy = parts[i].cy - my;
        const float d2 = dx*dx + dy*dy;
        if (d2 < best_d2) {
            best_d2 = d2;
            best = static_cast<int>(i);
        }
    }
    return best;
}

int Engine::pick_spring(float mx, float my) const {
    int best = -1;
    float best_d2 = pick_radius_ * pick_radius_;
    const auto& parts   = physics_.particles();
    const auto& springs = physics_.springs();
    const int n_parts = static_cast<int>(parts.size());
    for (std::size_t i = 0; i < springs.size(); ++i) {
        const auto& s = springs[i];
        if (!s.enabled) continue;
        if (s.a_idx < 0 || s.a_idx >= n_parts) continue;
        if (s.b_idx < 0 || s.b_idx >= n_parts) continue;
        const auto& pa = parts[s.a_idx];
        const auto& pb = parts[s.b_idx];
        const float vx = pb.cx - pa.cx;
        const float vy = pb.cy - pa.cy;
        const float wx = mx - pa.cx;
        const float wy = my - pa.cy;
        const float len2 = vx*vx + vy*vy;
        if (len2 < 1e-6f) continue;
        float t = (wx*vx + wy*vy) / len2;
        if (t < 0.0f) t = 0.0f;
        if (t > 1.0f) t = 1.0f;
        const float cx = pa.cx + vx * t;
        const float cy = pa.cy + vy * t;
        const float dx = mx - cx;
        const float dy = my - cy;
        const float d2 = dx*dx + dy*dy;
        if (d2 < best_d2) {
            best_d2 = d2;
            best = static_cast<int>(i);
        }
    }
    return best;
}

void Engine::render_frame() {
    int fb_w = 0, fb_h = 0;
    glfwGetFramebufferSize(window_, &fb_w, &fb_h);
    glViewport(0, 0, fb_w, fb_h);
    glClearColor(background_color_[0], background_color_[1], background_color_[2], 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);

#ifndef EKCHOUS_NO_IMGUI
    ImGui_ImplOpenGL3_NewFrame();
    ImGui_ImplGlfw_NewFrame();
    ImGui::NewFrame();

    // ---- Sliders + HUD ----
    ImGui::Begin("Softbody Engine");
    ImGui::Text("frame: %.2f ms  (%.1f FPS)",
                frame_time_avg_ms_,
                frame_time_avg_ms_ > 0.0 ? 1000.0 / frame_time_avg_ms_ : 0.0);
    ImGui::PlotLines("##frametime", frame_history_, kFrameHistory,
                     frame_history_idx_, nullptr,
                     0.0f, 33.3f,
                     ImVec2(ImGui::GetContentRegionAvail().x, 40.0f));
    ImGui::Text("particles: %zu   springs: %zu",
                physics_.particles().size(), physics_.springs().size());
    // Hover readout: stamped by the canvas mouse handling on the previous
    // pass. Useful for inspecting node IDs without clicking.
    if (hover_particle_idx_ >= 0 &&
        hover_particle_idx_ < static_cast<int>(physics_.particles().size())) {
        const auto& hp = physics_.particles()[hover_particle_idx_];
        const auto& hp_el = atoms::element(hp.element_id);
        const char* tpl_name = "?";
        // Best-effort: find a template that matches the particle's element.
        // (Templates aren't persistently tagged onto particles; this is just
        // a UI hint based on element_id.)
        for (const auto& tpl : templates_) {
            if (tpl.element_id == static_cast<int>(hp.element_id)) {
                tpl_name = tpl.name.c_str();
                break;
            }
        }
        ImGui::Text("hover idx=%d [%s, tpl: %s]  pos=(%.0f, %.0f)  vel=(%.2f, %.2f)",
                    hover_particle_idx_, hp_el.symbol, tpl_name,
                    hp.cx, hp.cy, hp.cx - hp.px, hp.cy - hp.py);
        ImGui::Text("       group=%d  ud=%u  flags=%c%c%c  rad=%.1f mass=%.2f",
                    hp.collision_group, static_cast<unsigned>(hp.user_data),
                    hp.enable_forces     ? 'F' : '-',
                    hp.enable_springs    ? 'S' : '-',
                    hp.enable_collisions ? 'C' : '-',
                    hp.rad, hp.mass);
        ImGui::Text("       rest_ticks=%u  at_rest=%s  cell=(%d, %d)",
                    static_cast<unsigned>(hp.rest_ticks),
                    hp.at_rest ? "yes" : "no",
                    static_cast<int>(hp.last_cell_x),
                    static_cast<int>(hp.last_cell_y));
    } else {
        ImGui::TextDisabled("hover: none (move cursor over a particle on canvas)");
        ImGui::TextDisabled(" ");
    }
    const auto& pt = physics_.last_timings();
    ImGui::Text("phases: springs %.2f ms  collisions %.2f ms  integrate %.2f ms",
                pt.springs_ms, pt.collisions_ms, pt.integrate_ms);
    if (ImGui::Button("Clear scene")) {
        physics_.reset();
    }

    constexpr ImGuiTreeNodeFlags kHeaderOpen = ImGuiTreeNodeFlags_DefaultOpen;

    if (ImGui::CollapsingHeader("Simulation", kHeaderOpen)) {
        if (ImGui::Button(paused_ ? "Resume" : "Pause")) paused_ = !paused_;
        ImGui::SameLine();
        if (ImGui::Button("Step one frame")) step_next_frame_ = true;
        ImGui::SameLine();
        ImGui::TextDisabled(paused_ ? "(paused)" : "(running)");
        slider_input_float("time scale",        &time_scale_,                           0.0f,  4.0f,  "%.2f");
        slider_input_int  ("iters: springs",    &physics_.params.iterations_springs,    1, 16);
        slider_input_int  ("iters: collisions", &physics_.params.iterations_collisions, 1, 16);
        slider_input_float("gravity y",         &physics_.params.gravity_y,             -2.0f, 2.0f, "%.2f");
        slider_input_float("gravity x",         &physics_.params.gravity_x,             -2.0f, 2.0f, "%.2f");
        slider_input_float("damp: bounds",      &physics_.particle_damp.bounds,         0.0f, 1.0f, "%.3f");
        slider_input_float("damp: collision",   &physics_.particle_damp.collision,      0.0f, 1.0f, "%.3f");
        slider_input_float("damp: velocity",    &physics_.particle_damp.velocity,       0.9f, 1.0f, "%.3f");
        slider_input_float("spring tear thresh", &physics_.params.spring_tear_threshold, 0.0f, 0.5f, "%.3f");
        ImGui::TextDisabled("tear: 0 disables; any spring whose |force| exceeds this gets snipped.");
        if (ImGui::Button("Purge torn springs")) {
            physics_.purge_torn_springs();
        }
        ImGui::SameLine();
        if (ImGui::Button("Recompute rest lengths")) {
            physics_.recompute_rest_lengths();
        }
        ImGui::TextDisabled("Recompute snaps every active spring's rest to its current length.");
    }

    if (ImGui::CollapsingHeader("Interaction", kHeaderOpen)) {
        slider_input_float("pick radius",   &pick_radius_,                4.0f,  80.0f, "%.0f");
        ImGui::Checkbox   ("mouse-spring drag (Hookean force) ", &use_mouse_spring_);
        if (use_mouse_spring_) {
            slider_input_float("spring stiffness", &mouse_spring_stiffness_, 0.01f, 2.0f,  "%.2f");
            slider_input_float("spring damping",   &mouse_spring_damping_,   0.0f,  2.0f,  "%.2f");
        } else {
            slider_input_float("drag stiffness", &drag_damp_,                0.05f, 1.0f,  "%.2f");
        }
        ImGui::Separator();
        ImGui::Checkbox   ("force gun (LMB drag)", &use_force_gun_);
        if (use_force_gun_) {
            ImGui::Checkbox   ("attract (pull) instead of push", &force_gun_attract_);
            slider_input_float("force strength", &force_gun_strength_, 0.0f,  4.0f,   "%.2f");
            slider_input_float("force radius",   &force_gun_radius_,   16.0f, 400.0f, "%.0f");
        }
        ImGui::Separator();
        ImGui::Checkbox   ("shade springs by tension", &shade_springs_by_tension_);
        ImGui::Checkbox   ("color particles by body (palette)", &color_by_body_);
        ImGui::Checkbox   ("color particles by user_data (palette)", &color_by_user_data_);
        ImGui::Separator();
        ImGui::ColorEdit3("background color", background_color_);
    }

    if (ImGui::CollapsingHeader("RMB action (right-click on canvas)")) {
        const char* kRmbLabels[] = {
            "0: drop ball",
            "1: drop static disk",
            "2: drag-to-place static line",
            "3: drop point gravity",
            "4: drop drag field",
            "5: drop flow-particle emitter",
            "6: drag-to-create spring",
            "7: delete particle",
            "8: delete spring",
            "9: drop atom",
        };
        ImGui::Combo("RMB does", &rmb_action_idx_, kRmbLabels, IM_ARRAYSIZE(kRmbLabels));
        ImGui::TextDisabled("Tool parameters live in their respective Body/Force-generator sections.");
        ImGui::Separator();
        ImGui::Checkbox("continuous (hold RMB to repeat)", &rmb_continuous_);
        if (rmb_continuous_) {
            slider_input_float("rate (Hz)", &rmb_rate_hz_, 1.0f, 60.0f, "%.1f");
            ImGui::TextDisabled("Line-drag and spring-drag actions are not repeated.");
        }
    }

    if (ImGui::CollapsingHeader("Atom field (EKCHOUS layer 4)", ImGuiTreeNodeFlags_DefaultOpen)) {
        ImGui::TextDisabled("Each atom stamps its element into surrounding cells.");
        ImGui::Checkbox("atom field enabled (rebuild each tick)", &atom_field_enabled_);
        ImGui::Checkbox("show atom field",                       &atom_field_show_);
        float cs = atom_field_cell_size_;
        if (slider_input_float("cell size (px)", &cs, 4.0f, 64.0f, "%.1f")) {
            atom_field_cell_size_ = cs;
            const int nx = static_cast<int>(std::ceil(canvas_w_ / atom_field_cell_size_));
            const int ny = static_cast<int>(std::ceil(canvas_h_ / atom_field_cell_size_));
            atom_field_.resize(nx, ny, atom_field_cell_size_);
        }
        slider_input_float("radius multiplier", &atom_field_radius_mul_,  0.5f, 4.0f, "%.2f");
        slider_input_float("alpha scale",       &atom_field_alpha_scale_, 0.0f, 1.0f, "%.2f");
        ImGui::Text("grid: %d x %d cells", atom_field_.nx(), atom_field_.ny());
    }

    if (ImGui::CollapsingHeader("Auto-bonds (EKCHOUS layer 3)", ImGuiTreeNodeFlags_DefaultOpen)) {
        ImGui::TextDisabled("Adjacent atoms with free valence form a Struct spring.");
        ImGui::TextDisabled("Existing spring tearing (Simulation panel) handles bond breakage.");
        ImGui::Checkbox("auto-bond enabled", &auto_bond_enabled_);
        slider_input_int  ("scan interval (ticks)", &auto_bond_interval_,     1, 120);
        slider_input_float("bond radius scale",     &auto_bond_radius_scale_, 0.1f, 4.0f, "%.2f");
        ImGui::Text("last pass formed: %d bonds", auto_bond_last_formed_);
        if (ImGui::Button("Run a bond scan now")) {
            auto_bond_last_formed_ =
                atoms::try_auto_bonds(physics_, auto_bond_radius_scale_);
            auto_bond_tick_counter_ = 0;
        }
    }

    if (ImGui::CollapsingHeader("Cell rest (EKCHOUS layer 2)", ImGuiTreeNodeFlags_DefaultOpen)) {
        ImGui::TextDisabled("Particle stops if its integer cell stays put for N ticks.");
        ImGui::Checkbox("cell rest enabled", &physics_.params.cell_rest_enabled);
        slider_input_int  ("rest threshold (ticks)", &physics_.params.rest_threshold_ticks, 1, 240);
        slider_input_float("rest cell size (px)",    &physics_.params.rest_cell_size,       2.0f, 64.0f, "%.1f");
        // Live count of at-rest particles.
        std::size_t rest_count = 0;
        for (const auto& p : physics_.particles()) if (p.at_rest) ++rest_count;
        ImGui::Text("at rest: %zu / %zu", rest_count, physics_.particles().size());
    }

    if (ImGui::CollapsingHeader("Particle templates", ImGuiTreeNodeFlags_DefaultOpen)) {
        ImGui::TextDisabled("Each template bundles element + radius + mass + flags + tag.");
        ImGui::TextDisabled("RMB action 9 ('drop atom') spawns a particle from the selected one.");

        // Template combo
        std::vector<const char*> tpl_labels;
        tpl_labels.reserve(templates_.size());
        for (auto& tpl : templates_) tpl_labels.push_back(tpl.name.c_str());
        ImGui::Combo("template", &selected_template_idx_, tpl_labels.data(),
                     static_cast<int>(tpl_labels.size()));

        if (ImGui::Button("New template")) {
            atoms::ParticleTemplate t;
            t.name = "Template " + std::to_string(templates_.size());
            templates_.push_back(t);
            selected_template_idx_ = static_cast<int>(templates_.size()) - 1;
        }
        ImGui::SameLine();
        if (ImGui::Button("Duplicate") && selected_template_idx_ >= 0 &&
            selected_template_idx_ < static_cast<int>(templates_.size())) {
            atoms::ParticleTemplate copy = templates_[selected_template_idx_];
            copy.name += " (copy)";
            templates_.insert(templates_.begin() + selected_template_idx_ + 1, copy);
            ++selected_template_idx_;
        }
        ImGui::SameLine();
        if (ImGui::Button("Delete") && templates_.size() > 1 &&
            selected_template_idx_ >= 0 &&
            selected_template_idx_ < static_cast<int>(templates_.size())) {
            templates_.erase(templates_.begin() + selected_template_idx_);
            if (selected_template_idx_ >= static_cast<int>(templates_.size())) {
                selected_template_idx_ = static_cast<int>(templates_.size()) - 1;
            }
        }

        // Inline editor for the selected template
        if (selected_template_idx_ >= 0 &&
            selected_template_idx_ < static_cast<int>(templates_.size())) {
            auto& t = templates_[selected_template_idx_];
            // Name
            std::strncpy(template_name_buf_, t.name.c_str(),
                         sizeof(template_name_buf_) - 1);
            template_name_buf_[sizeof(template_name_buf_) - 1] = '\0';
            ImGui::SetNextItemWidth(200);
            if (ImGui::InputText("name", template_name_buf_, sizeof(template_name_buf_))) {
                t.name = template_name_buf_;
            }

            std::vector<std::string> el_label_strs;
            std::vector<const char*> el_labels;
            el_label_strs.reserve(atoms::kNumElements);
            el_labels.reserve(atoms::kNumElements);
            for (int i = 0; i < atoms::kNumElements; ++i) {
                const auto& el = atoms::kElements[i];
                el_label_strs.emplace_back(std::string(el.symbol) + " — " + el.name);
                el_labels.push_back(el_label_strs.back().c_str());
            }
            ImGui::Combo("element", &t.element_id, el_labels.data(), atoms::kNumElements);
            slider_input_float("radius", &t.radius, 1.0f, 30.0f, "%.1f");
            slider_input_float("mass",   &t.mass,   0.1f, 50.0f, "%.2f");
            int ud_int = static_cast<int>(t.user_data);
            if (slider_input_int("user_data", &ud_int, 0, 255)) {
                t.user_data = static_cast<core::u32>(ud_int);
            }
            ImGui::Checkbox("enable_forces",     &t.enable_forces);
            ImGui::Checkbox("enable_springs",    &t.enable_springs);
            ImGui::Checkbox("enable_collisions", &t.enable_collisions);

            const auto& el_ref = atoms::element(t.element_id);
            ImGui::TextDisabled("element defaults: valence %d, bond r %.1f, strength %.2f",
                                el_ref.valence, el_ref.bond_radius, el_ref.bond_strength);

            ImGui::Separator();
            ImGui::TextUnformatted("Color override");
            ImGui::Checkbox("use color override", &t.use_color_override);
            if (t.use_color_override) {
                float col[3] = { t.color_override_r, t.color_override_g, t.color_override_b };
                if (ImGui::ColorEdit3("render color", col)) {
                    t.color_override_r = col[0];
                    t.color_override_g = col[1];
                    t.color_override_b = col[2];
                }
            }

            ImGui::Separator();
            ImGui::TextUnformatted("Linked tools");
            ImGui::TextDisabled("Off by default. Opt-in per template.");
            template_linkage_editor(t);
        }

        // Library I/O
        ImGui::Separator();
        ImGui::SetNextItemWidth(180);
        ImGui::InputText("library path", template_path_buf_, sizeof(template_path_buf_));
        ImGui::SameLine();
        if (ImGui::Button("Save lib")) {
            const bool ok = save_template_library();
            template_io_status_ = ok
                ? std::string("Saved → ") + template_path_buf_
                : std::string("Save FAILED for ") + template_path_buf_;
        }
        ImGui::SameLine();
        if (ImGui::Button("Load lib")) {
            const bool ok = load_template_library();
            template_io_status_ = ok
                ? std::string("Loaded ← ") + template_path_buf_
                : std::string("Load FAILED for ") + template_path_buf_;
        }
        if (!template_io_status_.empty()) {
            ImGui::TextDisabled("%s", template_io_status_.c_str());
        }

        if (ImGui::Button("Drop atom at canvas centre")) {
            drop_atom(canvas_w_ * 0.5f, canvas_h_ * 0.5f);
        }
        ImGui::SameLine();
        ImGui::Checkbox("color particles by element", &color_by_element_);
    }

    if (ImGui::CollapsingHeader("Selection (Shift+RMB drag)")) {
        ImGui::Text("selected particles: %zu", selected_particles_.size());
        ImGui::TextDisabled("Shift+RMB drag a box on the canvas to select.");
        if (ImGui::Button("Clear selection")) selected_particles_.clear();
        ImGui::SameLine();
        if (ImGui::Button("Delete selected")) {
            // Delete in DESCENDING index order so each remove_particle
            // only shifts indices below the current target.
            std::sort(selected_particles_.begin(), selected_particles_.end(),
                      std::greater<int>());
            for (int idx : selected_particles_) physics_.remove_particle(idx);
            selected_particles_.clear();
        }
        slider_input_int("user_data value", &sel_user_data_value_, 0, 16);
        if (ImGui::Button("Set user_data on selection")) {
            const core::u32 v = static_cast<core::u32>(sel_user_data_value_);
            auto& parts = physics_.particles();
            for (int idx : selected_particles_) {
                if (idx >= 0 && idx < static_cast<int>(parts.size())) {
                    parts[idx].user_data = v;
                }
            }
        }

        ImGui::Separator();
        slider_input_float("impulse x", &sel_impulse_x_, -32.0f, 32.0f, "%.2f");
        slider_input_float("impulse y", &sel_impulse_y_, -32.0f, 32.0f, "%.2f");
        if (ImGui::Button("Apply impulse to selection")) {
            // Verlet velocity is (cx - px). Subtracting from px adds to vel.
            auto& parts = physics_.particles();
            for (int idx : selected_particles_) {
                if (idx >= 0 && idx < static_cast<int>(parts.size())) {
                    parts[idx].px -= sel_impulse_x_;
                    parts[idx].py -= sel_impulse_y_;
                }
            }
        }

        ImGui::Separator();
        if (ImGui::Button("Pin selection")) {
            auto& parts = physics_.particles();
            for (int idx : selected_particles_) {
                if (idx >= 0 && idx < static_cast<int>(parts.size())) {
                    auto& p = parts[idx];
                    p.enable_forces     = false;
                    p.enable_springs    = false;
                    p.enable_collisions = false;
                }
            }
        }
        ImGui::SameLine();
        if (ImGui::Button("Unpin selection")) {
            auto& parts = physics_.particles();
            for (int idx : selected_particles_) {
                if (idx >= 0 && idx < static_cast<int>(parts.size())) {
                    auto& p = parts[idx];
                    p.enable_forces     = true;
                    p.enable_springs    = true;
                    p.enable_collisions = true;
                }
            }
        }
    }

    if (ImGui::CollapsingHeader("Scene I/O")) {
        ImGui::SetNextItemWidth(220.0f);
        ImGui::InputText("path", scene_path_buf_, sizeof(scene_path_buf_));
        ImGui::SameLine();
        if (ImGui::Button("Save")) {
            const bool ok = softbody::save_scene(scene_path_buf_, physics_,
                                                   flow_particles_, flow_field_);
            scene_io_status_ = ok ? std::string("Saved → ") + scene_path_buf_
                                  : std::string("Save FAILED for ") + scene_path_buf_;
        }
        ImGui::SameLine();
        if (ImGui::Button("Load")) {
            const bool ok = softbody::load_scene(scene_path_buf_, physics_,
                                                   flow_particles_, flow_field_);
            scene_io_status_ = ok ? std::string("Loaded ← ") + scene_path_buf_
                                  : std::string("Load FAILED for ") + scene_path_buf_;
        }
        if (!scene_io_status_.empty()) {
            ImGui::TextDisabled("%s", scene_io_status_.c_str());
        }
        ImGui::TextDisabled("Saves physics params, obstacles, force gens, emitters,");
        ImGui::TextDisabled("plus a particle + spring snapshot. Skips fluid/flow grids.");
    }

    if (ImGui::CollapsingHeader("Presets")) {
        if (ImGui::Button("balls in a bowl")) preset_bowl();
        ImGui::SameLine();
        if (ImGui::Button("cloth + fan"))     preset_cloth_fan();
        ImGui::SameLine();
        if (ImGui::Button("vortex tank"))     preset_vortex_tank();
        if (ImGui::Button("multi-body chaos")) preset_chaos();
        ImGui::SameLine();
        if (ImGui::Button("gravity wells"))   preset_gravity_wells();
        if (ImGui::Button("bridge"))           preset_bridge();
        ImGui::SameLine();
        if (ImGui::Button("tower stack"))      preset_tower();
        ImGui::TextDisabled("Replaces the current scene with the demo arrangement.");
    }

    if (ImGui::CollapsingHeader("Bodies (builders)", kHeaderOpen)) {
        if (ImGui::TreeNode("Ball (SoftBall)")) {
            slider_input_float("ring radius", &ball_ring_radius_, 20.0f, 200.0f, "%.0f");
            slider_input_float("node radius", &ball_node_radius_, 2.0f,  20.0f,  "%.1f");
            slider_input_int  ("bend dist",   &ball_bend_dist_,   1,     12);
            slider_input_float("spring damp", &ball_spring_damp_, 0.0f,  1.0f,   "%.2f");
            if (ImGui::Button("Drop ball at canvas centre")) {
                drop_ball(canvas_w_ * 0.5f, canvas_h_ * 0.25f);
            }
            ImGui::TreePop();
        }
        if (ImGui::TreeNode("Cloth (SoftGrid)")) {
            slider_input_int  ("nodes_x",   &grid_nodes_x_,     2,    64);
            slider_input_int  ("nodes_y",   &grid_nodes_y_,     2,    64);
            slider_input_float("node rad",  &grid_node_radius_, 2.0f, 16.0f, "%.1f");
            slider_input_int  ("bend dist", &grid_bend_dist_,   0,    8);
            slider_input_float("spring damp", &grid_spring_damp_, 0.0f, 1.0f,  "%.2f");
            ImGui::Checkbox("struct springs", &grid_create_struct_); ImGui::SameLine();
            ImGui::Checkbox("shear springs",  &grid_create_shear_);  ImGui::SameLine();
            ImGui::Checkbox("bend springs",   &grid_create_bend_);
            ImGui::Checkbox("pin top corners (hanging cloth)", &grid_pin_top_corners_);
            if (ImGui::Button("Drop cloth at canvas top")) {
                drop_cloth(canvas_w_ * 0.5f, canvas_h_ * 0.1f);
            }
            ImGui::TreePop();
        }
        if (ImGui::TreeNode("Rope (SoftRope)")) {
            slider_input_int  ("nodes",       &rope_num_nodes_,    2,    128);
            slider_input_float("length",      &rope_length_,       40.0f, 800.0f, "%.0f");
            slider_input_float("node rad",    &rope_node_radius_,  1.0f, 16.0f, "%.1f");
            slider_input_int  ("bend dist",   &rope_bend_dist_,    0,    8);
            slider_input_float("spring damp", &rope_spring_damp_,  0.0f, 1.0f,  "%.2f");
            ImGui::Checkbox("pin first node", &rope_pin_first_);
            if (ImGui::Button("Drop rope at canvas top-left")) {
                drop_rope(canvas_w_ * 0.15f, canvas_h_ * 0.15f);
            }
            ImGui::TreePop();
        }
        if (ImGui::TreeNode("Force generators")) {
            ImGui::Text("counts: gravities=%zu  drag fields=%zu",
                        physics_.point_gravities().size(),
                        physics_.drag_fields().size());
            slider_input_float("point gravity strength", &fg_pg_strength_, -2.0f, 2.0f, "%.2f");
            slider_input_float("point gravity radius",   &fg_pg_radius_,   20.0f, 600.0f, "%.0f");
            if (ImGui::Button("Drop attractor at centre")) {
                softbody::PointGravity g{canvas_w_ * 0.5f, canvas_h_ * 0.5f,
                                          std::fabs(fg_pg_strength_), fg_pg_radius_};
                physics_.add_point_gravity(g);
            }
            ImGui::SameLine();
            if (ImGui::Button("Drop repeller at centre")) {
                softbody::PointGravity g{canvas_w_ * 0.5f, canvas_h_ * 0.5f,
                                          -std::fabs(fg_pg_strength_), fg_pg_radius_};
                physics_.add_point_gravity(g);
            }
            slider_input_float("drag field half-width",  &fg_drag_half_width_,  20.0f, 400.0f, "%.0f");
            slider_input_float("drag field half-height", &fg_drag_half_height_, 20.0f, 400.0f, "%.0f");
            slider_input_float("drag field amount",      &fg_drag_amount_,      0.0f,  1.0f,   "%.3f");
            if (ImGui::Button("Drop drag field at centre")) {
                const float cx = canvas_w_ * 0.5f;
                const float cy = canvas_h_ * 0.5f;
                softbody::DragField f;
                f.aabb = softbody::AABB{cx - fg_drag_half_width_, cy - fg_drag_half_height_,
                                         cx + fg_drag_half_width_, cy + fg_drag_half_height_};
                f.drag = fg_drag_amount_;
                physics_.add_drag_field(f);
            }
            ImGui::SameLine();
            if (ImGui::Button("Clear force generators")) {
                physics_.clear_force_generators();
            }
            ImGui::TextDisabled("Generators run in the integrate phase, after springs+collisions.");
            ImGui::TreePop();
        }
        if (ImGui::TreeNode("Static obstacles")) {
            slider_input_float("disk radius",     &obs_radius_,         4.0f,  120.0f, "%.0f");
            slider_input_float("line thickness",  &obs_line_thickness_, 4.0f,  40.0f,  "%.0f");
            ImGui::Text("counts: disks=%zu  boxes=%zu  lines=%zu",
                        physics_.static_disks().size(),
                        physics_.static_boxes().size(),
                        physics_.static_lines().size());
            if (ImGui::Button("Drop disk at canvas centre")) {
                physics_.add_static_disk(canvas_w_ * 0.5f, canvas_h_ * 0.5f,
                                          obs_radius_);
            }
            if (ImGui::Button("Floor line across canvas")) {
                physics_.add_static_line(
                    obs_line_thickness_,                 canvas_h_ - obs_line_thickness_,
                    canvas_w_ - obs_line_thickness_,     canvas_h_ - obs_line_thickness_,
                    obs_line_thickness_);
            }
            ImGui::SameLine();
            if (ImGui::Button("Ramp (diagonal)")) {
                physics_.add_static_line(canvas_w_ * 0.1f, canvas_h_ * 0.4f,
                                          canvas_w_ * 0.6f, canvas_h_ * 0.8f,
                                          obs_line_thickness_);
            }
            if (ImGui::Button("Centre box (200x80)")) {
                const float cx = canvas_w_ * 0.5f;
                const float cy = canvas_h_ * 0.5f;
                physics_.add_static_box(cx - 100.0f, cy - 40.0f,
                                         cx + 100.0f, cy + 40.0f);
            }
            ImGui::SameLine();
            if (ImGui::Button("Clear obstacles")) {
                physics_.clear_static_obstacles();
            }
            ImGui::TextDisabled("Geometric obstacles: resolved outside the broadphase grid.");
            ImGui::TreePop();
        }
        if (ImGui::TreeNode("Star (SoftStar)")) {
            slider_input_int  ("num points",     &star_num_points_,    3,    16);
            slider_input_float("outer radius",   &star_outer_radius_,  20.0f, 200.0f, "%.0f");
            slider_input_float("inner radius",   &star_inner_radius_,  10.0f, 200.0f, "%.0f");
            slider_input_float("node radius",    &star_node_radius_,   2.0f,  16.0f,  "%.1f");
            slider_input_float("spring damp",    &star_spring_damp_,   0.0f,  1.0f,   "%.2f");
            if (ImGui::Button("Drop star at canvas centre")) {
                drop_star(canvas_w_ * 0.5f, canvas_h_ * 0.25f);
            }
            ImGui::TreePop();
        }
        if (ImGui::TreeNode("Disc (SoftDisc)")) {
            slider_input_float("outer radius",  &disc_outer_radius_, 20.0f, 200.0f, "%.0f");
            slider_input_float("node radius",   &disc_node_radius_,  2.0f,  16.0f,  "%.1f");
            slider_input_int  ("num rings",     &disc_num_rings_,    1,     8);
            slider_input_int  ("num angular",   &disc_num_angular_,  4,     32);
            slider_input_float("spring damp",   &disc_spring_damp_,  0.0f,  1.0f,   "%.2f");
            if (ImGui::Button("Drop disc at canvas centre")) {
                drop_disc(canvas_w_ * 0.5f, canvas_h_ * 0.25f);
            }
            ImGui::TreePop();
        }
        if (ImGui::TreeNode("Hex grid (SoftHexGrid)")) {
            slider_input_int  ("nodes_x",     &hex_nodes_x_,     2,    48);
            slider_input_int  ("nodes_y",     &hex_nodes_y_,     2,    48);
            slider_input_float("node radius", &hex_node_radius_, 2.0f, 12.0f, "%.1f");
            slider_input_float("spring damp", &hex_spring_damp_, 0.0f, 1.0f,  "%.2f");
            ImGui::Checkbox("pin top row (hanging hex sheet)", &hex_pin_top_row_);
            if (ImGui::Button("Drop hex grid at canvas top")) {
                drop_hex(canvas_w_ * 0.5f, canvas_h_ * 0.2f);
            }
            ImGui::TreePop();
        }
        if (ImGui::TreeNode("Blob (PoissonBlob)")) {
            slider_input_float("radius",         &blob_radius_,            20.0f, 200.0f, "%.0f");
            slider_input_float("node radius",    &blob_node_radius_,       2.0f,  16.0f,  "%.1f");
            slider_input_float("min node dist",  &blob_min_node_distance_, 4.0f,  40.0f,  "%.1f");
            slider_input_int  ("neighbors / node", &blob_neighbors_,       2,     12);
            slider_input_float("spring damp",    &blob_spring_damp_,       0.0f,  1.0f,   "%.2f");
            slider_input_int  ("seed",           &blob_seed_,              0,     1000000);
            if (ImGui::Button("Drop blob at canvas centre")) {
                drop_blob(canvas_w_ * 0.5f, canvas_h_ * 0.25f);
            }
            ImGui::TreePop();
        }
    }

    if (ImGui::CollapsingHeader("Stam fluid (Ctrl+LMB to paint)")) {
        ImGui::Checkbox("fluid enabled", &fluid_enabled_);
        ImGui::Checkbox("static obstacles block fluid", &fluid_block_obstacles_);
        int fluid_res = fluid_resolution_;
        if (slider_input_int("resolution", &fluid_res, 16, 256)) {
            fluid_resolution_ = fluid_res;
            fluid_.resize(fluid_resolution_);
        }
        slider_input_int  ("jacobi iters", &fluid_jacobi_iters_, 5, 120);
        slider_input_float("dt",          &fluid_dt_,            0.01f, 0.5f,   "%.3f");
        slider_input_float("viscosity",   &fluid_visc_,          0.0f,  0.01f,  "%.4f");
        slider_input_float("diffusion",   &fluid_diff_,          0.0f,  0.01f,  "%.4f");
        slider_input_float("paint density", &fluid_paint_density_, 0.0f,  200.0f, "%.0f");
        slider_input_float("paint force",   &fluid_paint_force_,   0.0f,  40.0f,  "%.1f");
        slider_input_float("vorticity confine eps", &fluid_.vorticity_eps, 0.0f, 50.0f, "%.2f");
        slider_input_float("density dissipation",   &fluid_.dissipation,   0.0f,  5.0f, "%.3f");
        ImGui::Separator();
        ImGui::TextUnformatted("Density (ρ + brightness)");
        slider_input_float("mass density (\xCF\x81)",  &fluid_.mass_density,       0.0f, 5.0f, "%.3f");
        slider_input_float("display brightness", &fluid_.display_brightness, 0.0f, 5.0f, "%.3f");
        slider_input_float("global fluid drag on particles", &fluid_drag_global_strength_,
                            0.0f, 5.0f, "%.3f");
        ImGui::Separator();
        ImGui::TextUnformatted("Temperature / buoyancy");
        slider_input_float("buoyancy",            &fluid_.buoyancy,             0.0f, 5.0f,  "%.3f");
        slider_input_float("ambient temperature", &fluid_.ambient_temperature, -5.0f, 5.0f,  "%.3f");
        slider_input_float("temp dissipation",    &fluid_.temperature_dissipation, 0.0f, 5.0f, "%.3f");
        slider_input_float("fluid gravity (\xCE\xB1)", &fluid_.gravity_density_coeff, 0.0f, 5.0f, "%.3f");
        ImGui::TextDisabled("Effective gravity = fluid gravity \xC3\x97 mass density \xC3\x97 cell density.");
        ImGui::Checkbox("hard ceiling enabled", &fluid_.ceiling_enabled);
        if (fluid_.ceiling_enabled) {
            slider_input_float("ceiling y (frac from top)",
                                &fluid_.ceiling_min_y_norm, 0.0f, 1.0f, "%.3f");
        }
        ImGui::Separator();
        ImGui::TextUnformatted("Colors");
        ImGui::ColorEdit3("fluid color (low density)",  fluid_color_lo_);
        ImGui::ColorEdit3("fluid color (high density)", fluid_color_hi_);
        ImGui::Separator();
        ImGui::Checkbox("show fluid density", &show_fluid_density_);
        ImGui::Checkbox("show temperature",   &show_fluid_temperature_);
        ImGui::Checkbox("metaball render (DwLiquidFX-style blobs)", &fluid_metaball_mode_);
        if (ImGui::Button("clear fluid")) fluid_.clear();
    }

    if (ImGui::CollapsingHeader("Fluid post-process")) {
        ImGui::TextDisabled("Filters applied to a render-side copy each frame, in order:");
        ImGui::TextDisabled("cutoff → blur → threshold → bloom → sobel → distance transform.");
        ImGui::Checkbox("cutoff (zero below)", &fluid_pp_cutoff_enabled_);
        if (fluid_pp_cutoff_enabled_) {
            slider_input_float("cutoff value", &fluid_pp_cutoff_, 0.0f, 100.0f, "%.1f");
        }
        ImGui::Checkbox("blur",        &fluid_pp_blur_enabled_);
        if (fluid_pp_blur_enabled_) {
            const char* kBlurModes[] = { "gaussian", "box", "pyramid", "SAT box" };
            ImGui::Combo("blur mode", &fluid_pp_blur_mode_, kBlurModes, IM_ARRAYSIZE(kBlurModes));
            slider_input_int("blur radius (px)", &fluid_pp_blur_radius_, 1, 16);
            if (fluid_pp_blur_mode_ == 2) {
                slider_input_int("pyramid levels", &fluid_pp_blur_pyr_levels_, 1, 4);
            }
        }
        ImGui::Checkbox("threshold (clip to [lo, hi])", &fluid_pp_threshold_enabled_);
        if (fluid_pp_threshold_enabled_) {
            slider_input_float("threshold lo", &fluid_pp_threshold_lo_, 0.0f,  100.0f, "%.1f");
            slider_input_float("threshold hi", &fluid_pp_threshold_hi_, 0.0f,  200.0f, "%.1f");
        }
        ImGui::Checkbox("bloom (extract+blur+add)", &fluid_pp_bloom_enabled_);
        if (fluid_pp_bloom_enabled_) {
            slider_input_float("bloom threshold",   &fluid_pp_bloom_threshold_, 0.0f,  100.0f, "%.1f");
            slider_input_float("bloom intensity",   &fluid_pp_bloom_intensity_, 0.0f,  4.0f,   "%.2f");
            slider_input_int  ("bloom blur radius", &fluid_pp_bloom_radius_,    1,     16);
        }
        ImGui::Checkbox("DoG (difference of gaussians)", &fluid_pp_dog_enabled_);
        if (fluid_pp_dog_enabled_) {
            slider_input_int("DoG: small radius", &fluid_pp_dog_small_, 1, 12);
            slider_input_int("DoG: large radius", &fluid_pp_dog_large_, 2, 16);
        }
        ImGui::Checkbox("median (rank, denoise)", &fluid_pp_median_enabled_);
        if (fluid_pp_median_enabled_) {
            slider_input_int("median radius", &fluid_pp_median_radius_, 1, 4);
        }
        ImGui::Checkbox("bilateral (edge-preserving smooth)", &fluid_pp_bilateral_enabled_);
        if (fluid_pp_bilateral_enabled_) {
            slider_input_int  ("bilateral radius",      &fluid_pp_bilateral_radius_,      1,    4);
            slider_input_float("bilateral sigma space", &fluid_pp_bilateral_sigma_space_, 0.5f, 8.0f,  "%.2f");
            slider_input_float("bilateral sigma range", &fluid_pp_bilateral_sigma_range_, 0.5f, 60.0f, "%.2f");
        }
        ImGui::Checkbox("dilate (max-filter)", &fluid_pp_dilate_enabled_);
        if (fluid_pp_dilate_enabled_) {
            slider_input_int("dilate radius", &fluid_pp_dilate_radius_, 1, 6);
        }
        ImGui::Checkbox("erode (min-filter)",  &fluid_pp_erode_enabled_);
        if (fluid_pp_erode_enabled_) {
            slider_input_int("erode radius",  &fluid_pp_erode_radius_,  1, 6);
        }
        ImGui::Checkbox("sobel (edge magnitude)", &fluid_pp_sobel_enabled_);
        ImGui::Checkbox("laplace (4-connected edge)", &fluid_pp_laplace_enabled_);
        ImGui::Checkbox("distance transform",     &fluid_pp_dt_enabled_);
        if (fluid_pp_dt_enabled_) {
            slider_input_float("DT max distance (cells)", &fluid_pp_dt_max_, 1.0f, 64.0f, "%.0f");
        }
        ImGui::Checkbox("multiply (scalar)", &fluid_pp_multiply_enabled_);
        if (fluid_pp_multiply_enabled_) {
            slider_input_float("multiplier", &fluid_pp_multiply_k_, 0.0f, 4.0f, "%.2f");
        }
        ImGui::Checkbox("gamma", &fluid_pp_gamma_enabled_);
        if (fluid_pp_gamma_enabled_) {
            slider_input_float("gamma", &fluid_pp_gamma_, 0.1f, 4.0f, "%.2f");
        }
        ImGui::Checkbox("normalize (rescale to [0,1] × 80)", &fluid_pp_normalize_);
        ImGui::Checkbox("depth-of-field (focus point)", &fluid_pp_dof_enabled_);
        if (fluid_pp_dof_enabled_) {
            slider_input_int  ("DoF max blur radius", &fluid_pp_dof_max_radius_, 1,    16);
            slider_input_float("DoF focus x",         &fluid_pp_dof_focus_x_,    0.0f, canvas_w_, "%.0f");
            slider_input_float("DoF focus y",         &fluid_pp_dof_focus_y_,    0.0f, canvas_h_, "%.0f");
            slider_input_float("DoF focus radius",    &fluid_pp_dof_focus_r_,    20.0f, 500.0f,   "%.0f");
        }
        ImGui::Checkbox("custom 3x3 convolution", &fluid_pp_custom_enabled_);
        if (fluid_pp_custom_enabled_) {
            for (int r = 0; r < 3; ++r) {
                for (int c = 0; c < 3; ++c) {
                    if (c > 0) ImGui::SameLine();
                    ImGui::PushID(r * 3 + c);
                    ImGui::SetNextItemWidth(56);
                    ImGui::InputFloat("##k", &fluid_pp_custom_kernel_[r * 3 + c],
                                      0.0f, 0.0f, "%.2f");
                    ImGui::PopID();
                }
            }
            if (ImGui::Button("identity")) {
                static const float k[9] = {0,0,0, 0,1,0, 0,0,0};
                std::copy(k, k + 9, fluid_pp_custom_kernel_);
            }
            ImGui::SameLine();
            if (ImGui::Button("sharpen")) {
                static const float k[9] = {0,-1,0, -1,5,-1, 0,-1,0};
                std::copy(k, k + 9, fluid_pp_custom_kernel_);
            }
            ImGui::SameLine();
            if (ImGui::Button("emboss")) {
                static const float k[9] = {-2,-1,0, -1,1,1, 0,1,2};
                std::copy(k, k + 9, fluid_pp_custom_kernel_);
            }
            ImGui::SameLine();
            if (ImGui::Button("edge")) {
                static const float k[9] = {-1,-1,-1, -1,8,-1, -1,-1,-1};
                std::copy(k, k + 9, fluid_pp_custom_kernel_);
            }
        }
    }

    if (ImGui::CollapsingHeader("Flow field (Shift+LMB to paint)")) {
        ImGui::Checkbox("flow enabled (applies force every step)", &flow_enabled_);
        slider_input_float("strength",       &flow_strength_,       0.0f,  0.5f,   "%.3f");
        slider_input_float("paint radius",   &flow_paint_radius_,   8.0f,  128.0f, "%.0f");
        slider_input_float("paint scale",    &flow_paint_scale_,    0.0f,  4.0f,   "%.2f");
        slider_input_float("preset strength",&flow_preset_strength_,0.1f,  4.0f,   "%.2f");
        ImGui::Checkbox("show field arrows", &show_flow_field_);
        if (ImGui::Button("clear field")) flow_field_.clear();
        ImGui::SameLine();
        if (ImGui::Button("wind →"))      flow_field_.make_wind(flow_preset_strength_, 0.0f);
        ImGui::SameLine();
        if (ImGui::Button("vortex"))      flow_field_.make_vortex(canvas_w_ * 0.5f, canvas_h_ * 0.5f, flow_preset_strength_);
        ImGui::SameLine();
        if (ImGui::Button("well"))        flow_field_.make_well  (canvas_w_ * 0.5f, canvas_h_ * 0.5f, flow_preset_strength_);
    }

    if (ImGui::CollapsingHeader("Flow particles")) {
        ImGui::Text("alive: %zu / %d   emitters: %zu",
                    flow_particles_.particle_count(), fp_max_count_,
                    flow_particles_.emitters().size());
        ImGui::Checkbox("global auto-emit (all emitters)", &fp_auto_emit_);
        ImGui::TextDisabled("Default emitter is at canvas centre. Use RMB action \"drop emitter\" to add more.");
        slider_input_int  ("default per-frame", &fp_emit_per_frame_,   0,    128);
        slider_input_float("default vx jitter", &fp_emit_vx_jitter_,   0.0f, 4.0f, "%.2f");
        slider_input_float("default vy jitter", &fp_emit_vy_jitter_,   0.0f, 4.0f, "%.2f");
        slider_input_float("damping",           &fp_damping_,          0.8f, 1.0f, "%.3f");
        slider_input_float("lifetime decay",    &fp_lifetime_decay_,   0.0f, 0.2f, "%.3f");
        slider_input_int  ("max count",         &fp_max_count_,        16,   16384);
        ImGui::Checkbox("show trails (8 positions, fading)", &fp_show_trails_);
        ImGui::Checkbox("velocity-based color", &fp_velocity_color_);
        if (fp_velocity_color_) {
            slider_input_float("color max speed", &fp_color_max_speed_, 0.5f, 30.0f, "%.1f");
            ImGui::ColorEdit3("stop 0 (0% speed)",   fp_color_stops_[0]);
            ImGui::ColorEdit3("stop 1 (25% speed)",  fp_color_stops_[1]);
            ImGui::ColorEdit3("stop 2 (50% speed)",  fp_color_stops_[2]);
            ImGui::ColorEdit3("stop 3 (75% speed)",  fp_color_stops_[3]);
            ImGui::ColorEdit3("stop 4 (100% speed)", fp_color_stops_[4]);
        }
        if (ImGui::Button("burst 500 at centre")) {
            flow_particles_.emit(canvas_w_ * 0.5f, canvas_h_ * 0.5f, 500,
                                 fp_emit_vx_jitter_, fp_emit_vy_jitter_, fp_max_count_);
        }
        ImGui::SameLine();
        if (ImGui::Button("clear flow particles")) {
            flow_particles_.clear();
        }
        ImGui::SameLine();
        if (ImGui::Button("clear emitters")) {
            flow_particles_.clear_emitters();
        }
    }

    if (ImGui::CollapsingHeader("Streamlines")) {
        ImGui::Checkbox("show streamlines", &show_streamlines_);
        int sc = stream_cols_, sr = stream_rows_;
        bool grid_changed = false;
        if (slider_input_int("cols", &sc, 4, 80)) grid_changed = true;
        if (slider_input_int("rows", &sr, 4, 80)) grid_changed = true;
        if (grid_changed) {
            stream_cols_ = sc;
            stream_rows_ = sr;
            streamlines_.resize_seeds(stream_cols_, stream_rows_, canvas_w_, canvas_h_);
        }
        slider_input_int  ("steps",     &stream_steps_,     2,    256);
        slider_input_float("step size", &stream_step_size_, 0.5f, 16.0f, "%.1f");
        slider_input_float("alpha",     &stream_alpha_,     0.0f, 1.0f,  "%.2f");
    }

    if (ImGui::CollapsingHeader("Couplings")) {
        ImGui::Checkbox("softbody pushes fluid", &sb_pushes_fluid_);
        slider_input_float("sb → fluid strength", &sb_to_fluid_strength_, 0.0f, 20.0f, "%.2f");
        ImGui::Checkbox("fluid pushes softbody",  &fluid_pushes_sb_);
        slider_input_float("fluid → sb drag",     &fluid_to_sb_drag_,     0.0f, 1.0f,  "%.3f");
        slider_input_float("flow particles' fluid scale", &fp_fluid_strength_, 0.0f, 4.0f, "%.2f");
        ImGui::TextDisabled("Flow particles always sample the flow field; this adds fluid velocity on top.");
    }

    ImGui::TextDisabled("LMB: drag particle (or force gun, if enabled).");
    ImGui::TextDisabled("Alt+LMB: cut springs.  Shift+LMB: paint flow field.  Ctrl+LMB: paint fluid.");
    ImGui::TextDisabled("RMB on canvas: configurable action (see RMB action panel).");
    ImGui::TextDisabled("Press F1 for full input reference.");
    ImGui::End();

    // F2 toggles the inspector window. Pin buttons let the user lock it
    // onto a specific target so the panel doesn't follow the cursor.
    if (ImGui::IsKeyPressed(ImGuiKey_F2)) show_inspector_ = !show_inspector_;
    if (show_inspector_) {
        ImGui::SetNextWindowSize(ImVec2(380, 540), ImGuiCond_FirstUseEver);
        ImGui::Begin("Inspector (F2 to toggle)", &show_inspector_);

        // Multi-selection summary takes priority over single-particle view.
        if (!selected_particles_.empty()) {
            ImGui::TextUnformatted("Multi-selection summary");
            ImGui::Separator();
            ImGui::Text("selected: %zu particles", selected_particles_.size());

            // Aggregate stats.
            const auto& parts = physics_.particles();
            int el_hist[atoms::kNumElements] = {0};
            double sum_x = 0, sum_y = 0, sum_mass = 0;
            float vmag_min =  std::numeric_limits<float>::infinity();
            float vmag_max = -std::numeric_limits<float>::infinity();
            int ud_min = INT_MAX, ud_max = -1;
            int valid = 0;
            for (int idx : selected_particles_) {
                if (idx < 0 || idx >= static_cast<int>(parts.size())) continue;
                ++valid;
                const auto& p = parts[idx];
                sum_x += p.cx;
                sum_y += p.cy;
                sum_mass += p.mass;
                const float vx = p.cx - p.px;
                const float vy = p.cy - p.py;
                const float vm = std::sqrt(vx*vx + vy*vy);
                if (vm < vmag_min) vmag_min = vm;
                if (vm > vmag_max) vmag_max = vm;
                if (p.element_id < atoms::kNumElements) ++el_hist[p.element_id];
                const int ud = static_cast<int>(p.user_data);
                if (ud < ud_min) ud_min = ud;
                if (ud > ud_max) ud_max = ud;
            }
            if (valid > 0) {
                ImGui::Text("avg pos: (%.0f, %.0f)",
                            sum_x / valid, sum_y / valid);
                ImGui::Text("total mass: %.2f   vel range: %.3f .. %.3f",
                            sum_mass, vmag_min, vmag_max);
                if (ud_max >= 0) {
                    ImGui::Text("user_data range: %d .. %d", ud_min, ud_max);
                }
                ImGui::Spacing();
                ImGui::TextUnformatted("element histogram:");
                for (int i = 0; i < atoms::kNumElements; ++i) {
                    if (el_hist[i] == 0) continue;
                    const auto& el = atoms::kElements[i];
                    ImGui::Text("  %s %s : %d",
                                el.symbol, el.name, el_hist[i]);
                }
            }

            ImGui::Spacing();
            if (ImGui::TreeNode("Selected indices")) {
                ImGui::BeginChild("##sellist", ImVec2(0, 140), true);
                for (int idx : selected_particles_) {
                    if (idx < 0 || idx >= static_cast<int>(parts.size())) continue;
                    const auto& p = parts[idx];
                    const auto& el = atoms::element(p.element_id);
                    ImGui::Text("  #%d  [%s]  ud=%u",
                                idx, el.symbol, static_cast<unsigned>(p.user_data));
                }
                ImGui::EndChild();
                ImGui::TreePop();
            }

            ImGui::Spacing();
            ImGui::Separator();
        }

        // Focused particle = pin > hover > first valid selected. Lets rich
        // rollover info stay visible whenever a selection is active, instead
        // of requiring the cursor to be parked on a particle.
        int p_idx = inspector_pinned_particle_;
        if (p_idx < 0) p_idx = hover_particle_idx_;
        if (p_idx < 0 && !selected_particles_.empty()) {
            for (int sidx : selected_particles_) {
                if (sidx >= 0 && sidx < static_cast<int>(physics_.particles().size())) {
                    p_idx = sidx;
                    break;
                }
            }
        }
        const int s_idx = inspector_pinned_spring_ >= 0
                            ? inspector_pinned_spring_
                            : hover_spring_idx_;

        ImGui::TextUnformatted("Particle (rollover / pin / first selected)");
        ImGui::Separator();
        if (p_idx >= 0 && p_idx < static_cast<int>(physics_.particles().size())) {
            auto& p = physics_.particles()[p_idx];
            ImGui::Text("idx %d  group %d", p_idx, p.collision_group);
            if (ImGui::Button(inspector_pinned_particle_ == p_idx
                              ? "Unpin particle" : "Pin to this particle")) {
                inspector_pinned_particle_ =
                    (inspector_pinned_particle_ == p_idx) ? -1 : p_idx;
            }
            slider_input_float("pos x",  &p.cx,  0.0f, canvas_w_, "%.1f");
            slider_input_float("pos y",  &p.cy,  0.0f, canvas_h_, "%.1f");
            slider_input_float("rad",    &p.rad, 1.0f, 40.0f, "%.1f");
            slider_input_float("mass",   &p.mass, 0.1f, 100.0f, "%.2f");
            int ud = static_cast<int>(p.user_data);
            if (slider_input_int("user_data", &ud, 0, 255)) {
                p.user_data = static_cast<core::u32>(ud);
            }
            ImGui::Checkbox("enable_forces",     &p.enable_forces);
            ImGui::Checkbox("enable_springs",    &p.enable_springs);
            ImGui::Checkbox("enable_collisions", &p.enable_collisions);
            const float vx = p.cx - p.px;
            const float vy = p.cy - p.py;
            ImGui::Text("vel: (%.3f, %.3f)   |v|=%.3f",
                        vx, vy, std::sqrt(vx*vx + vy*vy));
            ImGui::Text("element_id=%u  rest_ticks=%u  at_rest=%s",
                        static_cast<unsigned>(p.element_id),
                        static_cast<unsigned>(p.rest_ticks),
                        p.at_rest ? "yes" : "no");
            ImGui::Text("template_id=%u", static_cast<unsigned>(p.template_id));
        } else {
            ImGui::TextDisabled("hover, pin, or select a particle to inspect.");
        }

        ImGui::Spacing();
        ImGui::TextUnformatted("Spring");
        ImGui::Separator();
        if (s_idx >= 0 && s_idx < static_cast<int>(physics_.springs().size())) {
            auto& s = physics_.springs()[s_idx];
            ImGui::Text("idx %d  endpoints %d ↔ %d", s_idx, s.a_idx, s.b_idx);
            if (ImGui::Button(inspector_pinned_spring_ == s_idx
                              ? "Unpin spring" : "Pin to this spring")) {
                inspector_pinned_spring_ =
                    (inspector_pinned_spring_ == s_idx) ? -1 : s_idx;
            }
            if (slider_input_float("rest length", &s.dd_rest, 0.0f, 300.0f, "%.1f")) {
                s.dd_rest_sq = s.dd_rest * s.dd_rest;
            }
            slider_input_float("damp inc", &s.damp_inc, 0.0f, 1.0f, "%.2f");
            slider_input_float("damp dec", &s.damp_dec, 0.0f, 1.0f, "%.2f");
            ImGui::Checkbox("enabled", &s.enabled);
            ImGui::Text("last force: %.4f", s.force);
        } else {
            ImGui::TextDisabled("hover a spring, or pin one to inspect.");
        }

        // Template management — apply/save/edit for the focused particle.
        ImGui::Spacing();
        ImGui::TextUnformatted("Template");
        ImGui::Separator();
        const int n_sel_valid = static_cast<int>(selected_particles_.size());
        if (selected_template_idx_ >= 0 &&
            selected_template_idx_ < static_cast<int>(templates_.size())) {
            ImGui::Text("active template: \"%s\"  (idx %d)",
                        templates_[selected_template_idx_].name.c_str(),
                        selected_template_idx_);
        } else {
            ImGui::TextDisabled("no active template selected — pick one in the Templates panel.");
        }
        if (ImGui::Button(n_sel_valid > 0
                          ? "Apply active template to selected"
                          : "Apply active template (no selection)")
            && n_sel_valid > 0
            && selected_template_idx_ >= 0
            && selected_template_idx_ < static_cast<int>(templates_.size())) {
            const auto& t = templates_[selected_template_idx_];
            for (int sidx : selected_particles_) {
                if (sidx < 0 || sidx >= static_cast<int>(physics_.particles().size())) continue;
                auto& p = physics_.particles()[sidx];
                p.template_id       = static_cast<core::u8>(selected_template_idx_);
                p.element_id        = static_cast<core::u8>(t.element_id);
                p.mass              = t.mass;
                p.enable_forces     = t.enable_forces;
                p.enable_springs    = t.enable_springs;
                p.enable_collisions = t.enable_collisions;
                p.user_data         = t.user_data;
                p.set_radius(t.radius);
            }
        }
        ImGui::SetNextItemWidth(180);
        ImGui::InputText("new template name", inspector_new_tpl_name_, sizeof(inspector_new_tpl_name_));
        ImGui::SameLine();
        const bool can_save = p_idx >= 0 &&
                              p_idx < static_cast<int>(physics_.particles().size()) &&
                              inspector_new_tpl_name_[0] != '\0';
        if (ImGui::Button("Save focused as new template") && can_save) {
            const auto& p = physics_.particles()[p_idx];
            // Start from the source template's linkages (so user keeps their
            // tool wiring), then overwrite per-particle properties from the
            // particle's current live state.
            atoms::ParticleTemplate nt;
            if (p.template_id < templates_.size()) {
                nt = templates_[p.template_id];
            }
            nt.name              = inspector_new_tpl_name_;
            nt.element_id        = p.element_id;
            nt.radius            = p.rad;
            nt.mass              = p.mass;
            nt.enable_forces     = p.enable_forces;
            nt.enable_springs    = p.enable_springs;
            nt.enable_collisions = p.enable_collisions;
            nt.user_data         = p.user_data;
            templates_.push_back(nt);
            selected_template_idx_ = static_cast<int>(templates_.size()) - 1;
            inspector_new_tpl_name_[0] = '\0';
        }

        // Edit the focused particle's template's linkages inline.
        if (p_idx >= 0 && p_idx < static_cast<int>(physics_.particles().size())) {
            const std::size_t tid = physics_.particles()[p_idx].template_id;
            if (tid < templates_.size()) {
                ImGui::Spacing();
                ImGui::Text("Linkages of focused particle's template \"%s\":",
                            templates_[tid].name.c_str());
                ImGui::PushID("inspector_link");
                template_linkage_editor(templates_[tid]);
                ImGui::PopID();
            }
        }

        ImGui::End();
    }

    // F1 toggles a separate help window with the full input reference.
    if (ImGui::IsKeyPressed(ImGuiKey_F1)) show_help_ = !show_help_;
    if (show_help_) {
        ImGui::SetNextWindowSize(ImVec2(420, 420), ImGuiCond_FirstUseEver);
        ImGui::Begin("Help — input reference (F1 to toggle)", &show_help_);
        ImGui::TextUnformatted("Mouse on canvas");
        ImGui::Separator();
        ImGui::BulletText("LMB drag particle (or force gun if enabled in Interaction)");
        ImGui::BulletText("Shift+LMB paint flow field with cursor velocity");
        ImGui::BulletText("Ctrl+LMB paint fluid density + cursor velocity");
        ImGui::BulletText("Alt+LMB cut springs (drag across them)");
        ImGui::BulletText("RMB configurable action (see RMB action panel)");
        ImGui::BulletText("    0=drop ball  1=disk  2=line-drag  3=gravity");
        ImGui::BulletText("    4=drag field  5=emitter  6=spring-drag  7=delete particle");
        ImGui::BulletText("Shift+RMB drag-box selection (any RMB action)");
        ImGui::Spacing();
        ImGui::TextUnformatted("Keyboard");
        ImGui::Separator();
        ImGui::BulletText("F1 toggle this help window");
        ImGui::BulletText("F2 toggle the inspector (particle + spring editor)");
        ImGui::Spacing();
        ImGui::TextUnformatted("UI controls");
        ImGui::Separator();
        ImGui::BulletText("Each slider has a paired input box on the right: click to type");
        ImGui::BulletText("Drag the slider bar for continuous adjustment (clamped to range)");
        ImGui::BulletText("CollapsingHeaders fold sections out of the way");
        ImGui::BulletText("Bodies subgroups (TreeNodes) expand each builder's controls");
        ImGui::End();
    }

    // ---- Canvas ----
    ImGui::Begin("Canvas");
    const ImVec2 canvas_origin = ImGui::GetCursorScreenPos();
    ImGui::InvisibleButton("##canvas", ImVec2(canvas_w_, canvas_h_),
                           ImGuiButtonFlags_MouseButtonLeft |
                           ImGuiButtonFlags_MouseButtonRight);
    const bool canvas_hovered = ImGui::IsItemHovered();
    const bool lmb_held       = ImGui::IsMouseDown(ImGuiMouseButton_Left)  && canvas_hovered;
    const bool rmb_clicked    = ImGui::IsMouseClicked(ImGuiMouseButton_Right) && canvas_hovered;

    const ImVec2 mp = ImGui::GetMousePos();
    const float mx_canvas = mp.x - canvas_origin.x;
    const float my_canvas = mp.y - canvas_origin.y;

    // Right-click action depends on the selected RMB tool. Line mode is
    // drag-to-place (capture start, draw preview, commit on release); the
    // rest are point actions on click.
    const bool rmb_in_canvas = mx_canvas >= 0 && mx_canvas <= canvas_w_ &&
                               my_canvas >= 0 && my_canvas <= canvas_h_;
    const bool rmb_held      = ImGui::IsMouseDown(ImGuiMouseButton_Right);
    const bool rmb_released  = ImGui::IsMouseReleased(ImGuiMouseButton_Right);
    // Shift+RMB-press starts a drag-box selection regardless of the
    // currently-selected RMB action. shift_held is declared later in the
    // function for the LMB modifier dispatch, so check ImGuiIO directly.
    const bool sel_drag_start = rmb_clicked && ImGui::GetIO().KeyShift && rmb_in_canvas;
    if (sel_drag_start) {
        sel_drag_active_ = true;
        sel_start_x_ = mx_canvas;
        sel_start_y_ = my_canvas;
    } else if (rmb_clicked && rmb_in_canvas) {
        fire_rmb_action(mx_canvas, my_canvas);
        rmb_accum_time_ = 0.0f;  // first repeat happens after one full 1/Hz window
    }
    // Continuous emit while RMB is held. Skipped for drag-mode actions
    // (line idx 2, spring idx 6) which have inherent start/end semantics.
    {
        const bool drag_action = (rmb_action_idx_ == 2) || (rmb_action_idx_ == 6);
        if (rmb_continuous_ && rmb_held && rmb_in_canvas && !sel_drag_active_ &&
            !drag_action && rmb_rate_hz_ > 0.0f) {
            rmb_accum_time_ += ImGui::GetIO().DeltaTime;
            const float interval = 1.0f / rmb_rate_hz_;
            while (rmb_accum_time_ >= interval) {
                fire_rmb_action(mx_canvas, my_canvas);
                rmb_accum_time_ -= interval;
            }
        } else if (!rmb_held) {
            rmb_accum_time_ = 0.0f;
        }
    }
    if (sel_drag_active_ && rmb_released) {
        // Finalize the selection.
        selected_particles_.clear();
        const float xmin = std::min(sel_start_x_, mx_canvas);
        const float xmax = std::max(sel_start_x_, mx_canvas);
        const float ymin = std::min(sel_start_y_, my_canvas);
        const float ymax = std::max(sel_start_y_, my_canvas);
        const auto& parts_sel = physics_.particles();
        for (std::size_t i = 0; i < parts_sel.size(); ++i) {
            const auto& p = parts_sel[i];
            if (p.cx >= xmin && p.cx <= xmax && p.cy >= ymin && p.cy <= ymax) {
                selected_particles_.push_back(static_cast<int>(i));
            }
        }
        sel_drag_active_ = false;
    }
    if (rmb_line_drag_active_ && rmb_released) {
        physics_.add_static_line(rmb_line_start_x_, rmb_line_start_y_,
                                  mx_canvas, my_canvas, obs_line_thickness_);
        rmb_line_drag_active_ = false;
    }
    if (rmb_spring_drag_active_ && rmb_released) {
        const int end_idx = pick_particle(mx_canvas, my_canvas);
        if (rmb_spring_start_idx_ >= 0 && end_idx >= 0 &&
            rmb_spring_start_idx_ != end_idx) {
            physics_.add_spring(rmb_spring_start_idx_, end_idx,
                                 softbody::SpringType::Struct);
        }
        rmb_spring_drag_active_ = false;
        rmb_spring_start_idx_ = -1;
    }
    if (!rmb_held) {
        rmb_line_drag_active_ = false;
        rmb_spring_drag_active_ = false;
        rmb_spring_start_idx_ = -1;
        sel_drag_active_ = false;
    }

    // Left-drag picks/holds the nearest particle.
    const bool shift_held = ImGui::GetIO().KeyShift;
    const bool ctrl_held  = ImGui::GetIO().KeyCtrl;
    const bool alt_held   = ImGui::GetIO().KeyAlt;
    if (lmb_held && alt_held) {
        // Cutting tool: disable any spring whose pa-pb segment intersects
        // this frame's cursor travel.
        if (prev_mouse_canvas_x_ >= 0.0f) {
            const auto& parts = physics_.particles();
            for (auto& s : physics_.springs()) {
                if (!s.enabled) continue;
                const auto& pa = parts[s.a_idx];
                const auto& pb = parts[s.b_idx];
                if (segments_intersect(prev_mouse_canvas_x_, prev_mouse_canvas_y_,
                                        mx_canvas, my_canvas,
                                        pa.cx, pa.cy, pb.cx, pb.cy)) {
                    s.enabled = false;
                }
            }
        }
    } else if (lmb_held && use_force_gun_ && !shift_held && !ctrl_held) {
        // Radial force gun: every particle within force_gun_radius_ gets a
        // push (or pull) toward/away from the cursor. Quadratic falloff.
        const float r2 = force_gun_radius_ * force_gun_radius_;
        const float strength = force_gun_attract_ ? -force_gun_strength_ : force_gun_strength_;
        for (auto& p : physics_.particles()) {
            if (!p.enable_forces) continue;
            const float dx = p.cx - mx_canvas;
            const float dy = p.cy - my_canvas;
            const float d2 = dx*dx + dy*dy;
            if (d2 > r2 || d2 < 1.0f) continue;
            const float d = std::sqrt(d2);
            const float falloff = 1.0f - d / force_gun_radius_;
            const float fmag = strength * falloff * falloff;
            p.add_force(dx / d * fmag, dy / d * fmag);
        }
    } else if (lmb_held && ctrl_held && fluid_enabled_) {
        // Fluid paint: density + cursor velocity into the Stam grid.
        fluid_.add_density_at(mx_canvas, my_canvas, canvas_w_, canvas_h_,
                              fluid_paint_density_);
        if (prev_mouse_canvas_x_ >= 0.0f) {
            const float dx = mx_canvas - prev_mouse_canvas_x_;
            const float dy = my_canvas - prev_mouse_canvas_y_;
            fluid_.add_force_at(mx_canvas, my_canvas, canvas_w_, canvas_h_,
                                dx * fluid_paint_force_, dy * fluid_paint_force_);
        }
    } else if (lmb_held && shift_held) {
        // Flow-field paint mode: stamp cursor velocity into the field.
        if (prev_mouse_canvas_x_ >= 0.0f) {
            const float dx = mx_canvas - prev_mouse_canvas_x_;
            const float dy = my_canvas - prev_mouse_canvas_y_;
            flow_field_.stamp(mx_canvas, my_canvas, flow_paint_radius_,
                              dx * flow_paint_scale_, dy * flow_paint_scale_);
        }
    } else if (lmb_held) {
        if (dragged_particle_idx_ < 0) {
            dragged_particle_idx_ = pick_particle(mx_canvas, my_canvas);
        }
        if (dragged_particle_idx_ >= 0 &&
            dragged_particle_idx_ < static_cast<int>(physics_.particles().size())) {
            auto& p = physics_.particles()[dragged_particle_idx_];
            if (use_mouse_spring_) {
                // Hookean spring toward the cursor, with a velocity-damping
                // term that opposes motion. Force is fed to add_force, so
                // mass plays into how snappy the drag feels.
                const float dx = mx_canvas - p.cx;
                const float dy = my_canvas - p.cy;
                const float vx = p.cx - p.px;
                const float vy = p.cy - p.py;
                const float fx = dx * mouse_spring_stiffness_ - vx * mouse_spring_damping_;
                const float fy = dy * mouse_spring_stiffness_ - vy * mouse_spring_damping_;
                p.add_force(fx, fy);
            } else {
                p.move_to(mx_canvas, my_canvas, drag_damp_);
            }
        }
    } else {
        dragged_particle_idx_ = -1;
    }
    if (canvas_hovered) {
        prev_mouse_canvas_x_ = mx_canvas;
        prev_mouse_canvas_y_ = my_canvas;
        hover_particle_idx_  = pick_particle(mx_canvas, my_canvas);
        hover_spring_idx_    = pick_spring(mx_canvas, my_canvas);
    } else {
        prev_mouse_canvas_x_ = -1.0f;
        prev_mouse_canvas_y_ = -1.0f;
        hover_particle_idx_  = -1;
        hover_spring_idx_    = -1;
    }

    // Draw canvas backdrop + bounds.
    ImDrawList* dl = ImGui::GetWindowDrawList();
    dl->AddRectFilled(canvas_origin,
                      ImVec2(canvas_origin.x + canvas_w_, canvas_origin.y + canvas_h_),
                      IM_COL32(20, 22, 32, 255));
    dl->AddRect(canvas_origin,
                ImVec2(canvas_origin.x + canvas_w_, canvas_origin.y + canvas_h_),
                IM_COL32(80, 90, 110, 255));

    auto clamp_byte = [](int v) {
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    };

    // Draw fluid density UNDER everything (so soft bodies + arrows + particles
    // stay visible on top). Two modes:
    //   cells (default):    AddRectFilled per cell, blue→cyan→white gradient
    //   metaballs (toggle): AddCircleFilled per cell at radius 1.5×cell_w;
    //                       overlapping circles blend into smooth blobs,
    //                       emulating PixelFlow's DwLiquidFX without an FBO.
    //
    // Optional post-process: copy the inner NxN density into a scratch
    // buffer, run Gaussian/box blur + cutoff + threshold from Filters.h
    // before rendering. Simulation density is never modified.
    if (show_fluid_density_ && fluid_.n() > 0) {
        const int n = fluid_.n();
        const auto& dens = fluid_.density();
        const float cell_w = canvas_w_ / n;
        const float cell_h = canvas_h_ / n;

        const bool any_pp = fluid_pp_cutoff_enabled_ || fluid_pp_blur_enabled_ ||
                            fluid_pp_threshold_enabled_ || fluid_pp_bloom_enabled_ ||
                            fluid_pp_median_enabled_ || fluid_pp_bilateral_enabled_ ||
                            fluid_pp_dog_enabled_ || fluid_pp_sobel_enabled_ ||
                            fluid_pp_laplace_enabled_ || fluid_pp_dt_enabled_ ||
                            fluid_pp_multiply_enabled_ || fluid_pp_gamma_enabled_ ||
                            fluid_pp_custom_enabled_ || fluid_pp_normalize_ ||
                            fluid_pp_dilate_enabled_ || fluid_pp_erode_enabled_ ||
                            fluid_pp_dof_enabled_;
        if (any_pp) {
            fluid_pp_buffer_.assign(static_cast<std::size_t>(n) * n, 0.0f);
            for (int j = 0; j < n; ++j) {
                for (int i = 0; i < n; ++i) {
                    fluid_pp_buffer_[static_cast<std::size_t>(j) * n + i] =
                        dens[(i + 1) + (n + 2) * (j + 1)];
                }
            }
            if (fluid_pp_cutoff_enabled_) {
                softbody::cutoff_below(fluid_pp_buffer_, fluid_pp_cutoff_);
            }
            if (fluid_pp_blur_enabled_) {
                switch (fluid_pp_blur_mode_) {
                    case 0: softbody::gaussian_blur(fluid_pp_buffer_, n, n, fluid_pp_blur_radius_); break;
                    case 1: softbody::box_blur     (fluid_pp_buffer_, n, n, fluid_pp_blur_radius_); break;
                    case 2: softbody::gaussian_blur_pyramid(fluid_pp_buffer_, n, n,
                                                              fluid_pp_blur_radius_,
                                                              fluid_pp_blur_pyr_levels_); break;
                    case 3: softbody::sat_box_blur(fluid_pp_buffer_, n, n, fluid_pp_blur_radius_); break;
                }
            }
            if (fluid_pp_threshold_enabled_) {
                softbody::threshold(fluid_pp_buffer_,
                                     fluid_pp_threshold_lo_,
                                     fluid_pp_threshold_hi_);
            }
            if (fluid_pp_bloom_enabled_) {
                softbody::bloom(fluid_pp_buffer_, n, n,
                                 fluid_pp_bloom_radius_,
                                 fluid_pp_bloom_threshold_,
                                 fluid_pp_bloom_intensity_);
            }
            if (fluid_pp_median_enabled_) {
                softbody::median_filter(fluid_pp_buffer_, n, n, fluid_pp_median_radius_);
            }
            if (fluid_pp_bilateral_enabled_) {
                softbody::bilateral_filter(fluid_pp_buffer_, n, n,
                                            fluid_pp_bilateral_radius_,
                                            fluid_pp_bilateral_sigma_space_,
                                            fluid_pp_bilateral_sigma_range_);
            }
            if (fluid_pp_dilate_enabled_) {
                softbody::max_filter(fluid_pp_buffer_, n, n, fluid_pp_dilate_radius_);
            }
            if (fluid_pp_erode_enabled_) {
                softbody::min_filter(fluid_pp_buffer_, n, n, fluid_pp_erode_radius_);
            }
            if (fluid_pp_dog_enabled_) {
                softbody::difference_of_gaussians(fluid_pp_buffer_, n, n,
                                                   fluid_pp_dog_small_,
                                                   fluid_pp_dog_large_);
            }
            if (fluid_pp_sobel_enabled_) {
                softbody::sobel_magnitude(fluid_pp_buffer_, n, n);
            }
            if (fluid_pp_laplace_enabled_) {
                softbody::laplace(fluid_pp_buffer_, n, n);
            }
            if (fluid_pp_dt_enabled_) {
                softbody::distance_transform(fluid_pp_buffer_, n, n,
                                              0.0f, fluid_pp_dt_max_);
            }
            if (fluid_pp_multiply_enabled_) {
                softbody::multiply_scalar(fluid_pp_buffer_, fluid_pp_multiply_k_);
            }
            if (fluid_pp_gamma_enabled_) {
                softbody::gamma_correct(fluid_pp_buffer_, fluid_pp_gamma_);
            }
            if (fluid_pp_custom_enabled_) {
                std::vector<float> k(
                    fluid_pp_custom_kernel_, fluid_pp_custom_kernel_ + 9);
                softbody::convolution(fluid_pp_buffer_, n, n, k, 3);
            }
            if (fluid_pp_dof_enabled_) {
                softbody::depth_of_field(fluid_pp_buffer_, n, n,
                                          fluid_pp_dof_max_radius_,
                                          fluid_pp_dof_focus_x_,
                                          fluid_pp_dof_focus_y_,
                                          fluid_pp_dof_focus_r_,
                                          canvas_w_, canvas_h_);
            }
            if (fluid_pp_normalize_) {
                softbody::normalize_global(fluid_pp_buffer_);
                // Re-scale to a visible density range so render math doesn't
                // wash everything out (default render assumes density ~0..60).
                softbody::multiply_scalar(fluid_pp_buffer_, 80.0f);
            }
        }

        auto sample_d = [&](int i, int j) -> float {
            if (any_pp) {
                // Buffer is indexed [0, n) on inner cells.
                return fluid_pp_buffer_[static_cast<std::size_t>(j - 1) * n + (i - 1)];
            }
            return dens[i + (n + 2) * j];
        };

        for (int j = 1; j <= n; ++j) {
            for (int i = 1; i <= n; ++i) {
                float d = sample_d(i, j);
                d *= fluid_.display_brightness;
                if (d < 0.02f) continue;
                int a = static_cast<int>(d * 6.0f);
                if (a > 220) a = 220;
                // Lerp lo→hi by normalized density (saturating around d=40).
                float t = d / 40.0f;
                if (t < 0) t = 0; else if (t > 1) t = 1;
                int r = clamp_byte(static_cast<int>(
                    (fluid_color_lo_[0] + (fluid_color_hi_[0] - fluid_color_lo_[0]) * t) * 255.0f));
                int g = clamp_byte(static_cast<int>(
                    (fluid_color_lo_[1] + (fluid_color_hi_[1] - fluid_color_lo_[1]) * t) * 255.0f));
                int b = clamp_byte(static_cast<int>(
                    (fluid_color_lo_[2] + (fluid_color_hi_[2] - fluid_color_lo_[2]) * t) * 255.0f));
                if (fluid_metaball_mode_) {
                    const float cxw = (i - 0.5f) * cell_w;
                    const float cyw = (j - 0.5f) * cell_h;
                    dl->AddCircleFilled(
                        ImVec2(canvas_origin.x + cxw, canvas_origin.y + cyw),
                        cell_w * 1.5f, IM_COL32(r, g, b, a), 14);
                } else {
                    const float x0 = (i - 1) * cell_w;
                    const float y0 = (j - 1) * cell_h;
                    dl->AddRectFilled(
                        ImVec2(canvas_origin.x + x0,            canvas_origin.y + y0),
                        ImVec2(canvas_origin.x + x0 + cell_w,   canvas_origin.y + y0 + cell_h),
                        IM_COL32(r, g, b, a));
                }
            }
        }
    }

    // Temperature overlay — red where T > ambient, blue where T < ambient.
    if (show_fluid_temperature_ && fluid_.n() > 0) {
        const int n = fluid_.n();
        const auto& T = fluid_.temperature();
        const float cell_w = canvas_w_ / n;
        const float cell_h = canvas_h_ / n;
        const float amb = fluid_.ambient_temperature;
        for (int j = 1; j <= n; ++j) {
            for (int i = 1; i <= n; ++i) {
                const float t = T[i + (n + 2) * j] - amb;
                if (std::fabs(t) < 0.05f) continue;
                const float mag = std::fabs(t);
                int a = static_cast<int>(mag * 60.0f);
                if (a > 180) a = 180;
                int r = t > 0 ? 220 : 40;
                int g = 40;
                int b = t > 0 ? 40  : 220;
                const float x0 = (i - 1) * cell_w;
                const float y0 = (j - 1) * cell_h;
                dl->AddRectFilled(
                    ImVec2(canvas_origin.x + x0,          canvas_origin.y + y0),
                    ImVec2(canvas_origin.x + x0 + cell_w, canvas_origin.y + y0 + cell_h),
                    IM_COL32(r, g, b, a));
            }
        }
    }

    // Streamlines, rendered on top of fluid density but under arrows /
    // particles. Each polyline fades from bright to dim across its length.
    if (show_streamlines_ && fluid_enabled_) {
        for (const auto& line : streamlines_.lines()) {
            if (line.xs.size() < 2) continue;
            const float n = static_cast<float>(line.xs.size() - 1);
            for (std::size_t k = 0; k + 1 < line.xs.size(); ++k) {
                const float t = static_cast<float>(k) / n;
                const int a = clamp_byte(static_cast<int>(stream_alpha_ * 220.0f * (1.0f - t)));
                const ImU32 col = IM_COL32(220, 230, 255, a);
                dl->AddLine(
                    ImVec2(canvas_origin.x + line.xs[k],     canvas_origin.y + line.ys[k]),
                    ImVec2(canvas_origin.x + line.xs[k + 1], canvas_origin.y + line.ys[k + 1]),
                    col, 1.0f);
            }
        }
    }

    // Draw flow-field arrows underneath particles so they show through.
    if (show_flow_field_) {
        const int nx = flow_field_.nx();
        const int ny = flow_field_.ny();
        const float cs = flow_field_.cell_size();
        const auto& fvx = flow_field_.vx();
        const auto& fvy = flow_field_.vy();
        for (int y = 0; y < ny; ++y) {
            for (int x = 0; x < nx; ++x) {
                const std::size_t i = static_cast<std::size_t>(y) * nx + x;
                const float vx = fvx[i];
                const float vy = fvy[i];
                const float mag2 = vx*vx + vy*vy;
                if (mag2 < 1e-6f) continue;
                const float cxw = (x + 0.5f) * cs;
                const float cyw = (y + 0.5f) * cs;
                const float arrow_scale = 8.0f;
                const float ex = cxw + vx * arrow_scale;
                const float ey = cyw + vy * arrow_scale;
                const float mag = std::sqrt(mag2);
                // Map magnitude → green→yellow→red.
                const int r = std::min(255, static_cast<int>(mag * 200.0f));
                const int g = std::min(255, static_cast<int>(80.0f + mag * 80.0f));
                const ImU32 col = IM_COL32(r, g, 60, 200);
                dl->AddLine(ImVec2(canvas_origin.x + cxw, canvas_origin.y + cyw),
                            ImVec2(canvas_origin.x + ex,  canvas_origin.y + ey),
                            col, 1.0f);
                dl->AddCircleFilled(ImVec2(canvas_origin.x + ex, canvas_origin.y + ey),
                                    1.5f, col, 6);
            }
        }
    }

    // Draw springs as lines. When tension-shading is on, map |force| to a
    // red/green gradient (PixelFlow's DwSoftBody2D.shade_springs_by_tension
    // does the same: r = force*10000, g = force*1000).
    const auto& parts = physics_.particles();
    const auto& sprs  = physics_.springs();
    for (const auto& s : sprs) {
        const auto& pa = parts[s.a_idx];
        const auto& pb = parts[s.b_idx];
        ImU32 col;
        if (!s.enabled) {
            // Torn spring — kept in the data for inspection but render dim.
            col = IM_COL32(80, 30, 30, 80);
        } else if (shade_springs_by_tension_) {
            const float tension = s.force < 0 ? -s.force : s.force;
            const int r = clamp_byte(static_cast<int>(tension * 10000.0f));
            const int g = clamp_byte(static_cast<int>(tension * 1000.0f));
            col = IM_COL32(r, g, 0, 220);
        } else {
            col = (s.type == softbody::SpringType::Bend)
                ? IM_COL32(70, 90, 130, 140)
                : IM_COL32(150, 170, 200, 220);
        }
        dl->AddLine(ImVec2(canvas_origin.x + pa.cx, canvas_origin.y + pa.cy),
                    ImVec2(canvas_origin.x + pb.cx, canvas_origin.y + pb.cy),
                    col, 1.0f);
    }

    // Draw flow-particle emitter icons (white crosshair + pulse ring) under
    // everything else so they don't obscure dynamic content.
    for (const auto& em : flow_particles_.emitters()) {
        if (!em.enabled) continue;
        const ImU32 col = IM_COL32(240, 240, 250, 200);
        const float k = 6.0f;
        dl->AddLine(ImVec2(canvas_origin.x + em.x - k, canvas_origin.y + em.y),
                    ImVec2(canvas_origin.x + em.x + k, canvas_origin.y + em.y),
                    col, 1.5f);
        dl->AddLine(ImVec2(canvas_origin.x + em.x, canvas_origin.y + em.y - k),
                    ImVec2(canvas_origin.x + em.x, canvas_origin.y + em.y + k),
                    col, 1.5f);
        dl->AddCircle(ImVec2(canvas_origin.x + em.x, canvas_origin.y + em.y),
                      3.0f, col, 12, 1.0f);
    }

    // Draw flow particles UNDER softbody particles so the soft bodies stay
    // legible. Each dot is alpha-faded by lifetime and colored white→
    // yellow→orange by speed.
    for (const auto& fp : flow_particles_.particles()) {
        if (!fp.alive) continue;
        const float speed = std::sqrt(fp.vx * fp.vx + fp.vy * fp.vy);
        int r, g, b;
        if (fp_velocity_color_) {
            const float vmax = fp_color_max_speed_ > 0.001f ? fp_color_max_speed_ : 0.001f;
            float t = speed / vmax;
            if (t < 0.0f) t = 0.0f; else if (t > 1.0f) t = 1.0f;
            // 5 user-pickable stops at t = 0, 0.25, 0.5, 0.75, 1.
            const float t4 = t * 4.0f;
            int seg = static_cast<int>(t4);
            if (seg < 0) seg = 0; else if (seg > 3) seg = 3;
            const float u = t4 - seg;
            const float rf = fp_color_stops_[seg][0] * (1 - u) + fp_color_stops_[seg + 1][0] * u;
            const float gf = fp_color_stops_[seg][1] * (1 - u) + fp_color_stops_[seg + 1][1] * u;
            const float bf = fp_color_stops_[seg][2] * (1 - u) + fp_color_stops_[seg + 1][2] * u;
            r = clamp_byte(static_cast<int>(rf * 255.0f));
            g = clamp_byte(static_cast<int>(gf * 255.0f));
            b = clamp_byte(static_cast<int>(bf * 255.0f));
        } else {
            r = 255;
            g = clamp_byte(static_cast<int>(255.0f - speed * 60.0f));
            b = clamp_byte(static_cast<int>(255.0f - speed * 180.0f));
        }
        const int a = clamp_byte(static_cast<int>(fp.lifetime * 220.0f));
        // Optional trail: oldest → newest, alpha-faded.
        if (fp_show_trails_ && fp.trail_count >= 2) {
            for (int k = 0; k + 1 < fp.trail_count; ++k) {
                const int idx_a = (fp.trail_head - fp.trail_count + k
                                   + softbody::FlowParticle::kTrailLen)
                                  % softbody::FlowParticle::kTrailLen;
                const int idx_b = (idx_a + 1) % softbody::FlowParticle::kTrailLen;
                const float t = static_cast<float>(k) / fp.trail_count;
                const int ta = clamp_byte(static_cast<int>(fp.lifetime * t * 180.0f));
                dl->AddLine(
                    ImVec2(canvas_origin.x + fp.trail_xs[idx_a], canvas_origin.y + fp.trail_ys[idx_a]),
                    ImVec2(canvas_origin.x + fp.trail_xs[idx_b], canvas_origin.y + fp.trail_ys[idx_b]),
                    IM_COL32(r, g, b, ta), 1.0f);
            }
            // Connect last trail point to current head position.
            const int last_idx = (fp.trail_head - 1 + softbody::FlowParticle::kTrailLen)
                                 % softbody::FlowParticle::kTrailLen;
            dl->AddLine(
                ImVec2(canvas_origin.x + fp.trail_xs[last_idx], canvas_origin.y + fp.trail_ys[last_idx]),
                ImVec2(canvas_origin.x + fp.x,                  canvas_origin.y + fp.y),
                IM_COL32(r, g, b, a), 1.0f);
        }
        dl->AddCircleFilled(ImVec2(canvas_origin.x + fp.x, canvas_origin.y + fp.y),
                            1.5f, IM_COL32(r, g, b, a), 6);
    }

    // EKCHOUS layer 4: draw the atom field as colored cells under all
    // dynamic content. Cells render only when intensity is meaningful.
    if (atom_field_show_ && atom_field_enabled_ && atom_field_.nx() > 0) {
        const int af_nx = atom_field_.nx();
        const int af_ny = atom_field_.ny();
        const float af_cs = atom_field_.cell_size();
        const auto& af_int = atom_field_.intensity();
        const auto& af_dom = atom_field_.dominant();
        for (int y = 0; y < af_ny; ++y) {
            for (int x = 0; x < af_nx; ++x) {
                const std::size_t i = static_cast<std::size_t>(y) * af_nx + x;
                const float w = af_int[i];
                if (w < 0.05f) continue;
                const auto& el = atoms::element(af_dom[i]);
                int a = static_cast<int>(w * 255.0f * atom_field_alpha_scale_);
                if (a > 255) a = 255;
                const ImU32 col = IM_COL32(el.r, el.g, el.b, a);
                const float x0 = x * af_cs;
                const float y0 = y * af_cs;
                dl->AddRectFilled(
                    ImVec2(canvas_origin.x + x0,         canvas_origin.y + y0),
                    ImVec2(canvas_origin.x + x0 + af_cs, canvas_origin.y + y0 + af_cs),
                    col);
            }
        }
    }

    // Draw force generators UNDER everything else: gravity sources show their
    // influence radius, drag fields show their box. Visually obvious so the
    // user can see what's affecting nearby particles.
    {
        for (const auto& pg : physics_.point_gravities()) {
            const bool attract = pg.strength >= 0.0f;
            const ImU32 fill = attract
                ? IM_COL32( 80, 140, 230, 40)
                : IM_COL32(230, 100,  80, 40);
            const ImU32 ring = attract
                ? IM_COL32(120, 180, 255, 200)
                : IM_COL32(255, 130, 100, 200);
            if (pg.max_radius > 0.0f) {
                dl->AddCircleFilled(
                    ImVec2(canvas_origin.x + pg.cx, canvas_origin.y + pg.cy),
                    pg.max_radius, fill, 36);
                dl->AddCircle(
                    ImVec2(canvas_origin.x + pg.cx, canvas_origin.y + pg.cy),
                    pg.max_radius, ring, 36, 1.5f);
            }
            // Centre cross.
            const float k = 6.0f;
            dl->AddLine(ImVec2(canvas_origin.x + pg.cx - k, canvas_origin.y + pg.cy),
                        ImVec2(canvas_origin.x + pg.cx + k, canvas_origin.y + pg.cy),
                        ring, 1.5f);
            dl->AddLine(ImVec2(canvas_origin.x + pg.cx, canvas_origin.y + pg.cy - k),
                        ImVec2(canvas_origin.x + pg.cx, canvas_origin.y + pg.cy + k),
                        ring, 1.5f);
        }
        for (const auto& df : physics_.drag_fields()) {
            const ImU32 fill = IM_COL32(180, 180, 110, 35);
            const ImU32 edge = IM_COL32(220, 220, 140, 200);
            dl->AddRectFilled(
                ImVec2(canvas_origin.x + df.aabb.min_x, canvas_origin.y + df.aabb.min_y),
                ImVec2(canvas_origin.x + df.aabb.max_x, canvas_origin.y + df.aabb.max_y),
                fill);
            dl->AddRect(
                ImVec2(canvas_origin.x + df.aabb.min_x, canvas_origin.y + df.aabb.min_y),
                ImVec2(canvas_origin.x + df.aabb.max_x, canvas_origin.y + df.aabb.max_y),
                edge, 0.0f, 0, 1.2f);
        }
    }

    // Draw static obstacles BEFORE softbody particles so dynamic content
    // overlays them. Distinct gray reads as level geometry.
    {
        const ImU32 obs_fill = IM_COL32(120, 120, 130, 230);
        const ImU32 obs_edge = IM_COL32( 30,  30,  35, 220);
        for (const auto& d : physics_.static_disks()) {
            dl->AddCircleFilled(
                ImVec2(canvas_origin.x + d.disk.cx, canvas_origin.y + d.disk.cy),
                d.disk.radius, obs_fill, 28);
            dl->AddCircle(
                ImVec2(canvas_origin.x + d.disk.cx, canvas_origin.y + d.disk.cy),
                d.disk.radius, obs_edge, 28, 1.5f);
        }
        for (const auto& b : physics_.static_boxes()) {
            dl->AddRectFilled(
                ImVec2(canvas_origin.x + b.aabb.min_x, canvas_origin.y + b.aabb.min_y),
                ImVec2(canvas_origin.x + b.aabb.max_x, canvas_origin.y + b.aabb.max_y),
                obs_fill);
            dl->AddRect(
                ImVec2(canvas_origin.x + b.aabb.min_x, canvas_origin.y + b.aabb.min_y),
                ImVec2(canvas_origin.x + b.aabb.max_x, canvas_origin.y + b.aabb.max_y),
                obs_edge, 0.0f, 0, 1.5f);
        }
        for (const auto& l : physics_.static_lines()) {
            dl->AddLine(
                ImVec2(canvas_origin.x + l.ax, canvas_origin.y + l.ay),
                ImVec2(canvas_origin.x + l.bx, canvas_origin.y + l.by),
                obs_fill, l.thickness * 2.0f);
        }
    }

    // Draw particles as filled circles.
    for (std::size_t i = 0; i < parts.size(); ++i) {
        const auto& p = parts[i];
        ImU32 col;
        if (!p.enable_forces) {
            // Static obstacle — distinct gray so it reads as level geometry.
            col = IM_COL32(120, 120, 130, 240);
        } else if (static_cast<int>(i) == dragged_particle_idx_) {
            col = IM_COL32(255, 240, 120, 255);
        } else if (color_by_element_) {
            const std::size_t tid = p.template_id;
            if (tid < templates_.size() && templates_[tid].use_color_override) {
                const auto& t = templates_[tid];
                col = IM_COL32(clamp_byte(static_cast<int>(t.color_override_r * 255.0f)),
                                clamp_byte(static_cast<int>(t.color_override_g * 255.0f)),
                                clamp_byte(static_cast<int>(t.color_override_b * 255.0f)),
                                255);
            } else {
                const auto& el = atoms::element(p.element_id);
                col = IM_COL32(el.r, el.g, el.b, 255);
            }
        } else if (color_by_user_data_) {
            col = body_palette_color(static_cast<int>(p.user_data));
        } else if (color_by_body_) {
            col = body_palette_color(p.collision_group);
        } else {
            col = particle_color(p.collision_count);
        }
        dl->AddCircleFilled(ImVec2(canvas_origin.x + p.cx, canvas_origin.y + p.cy),
                            p.rad, col, 12);
        dl->AddCircle(ImVec2(canvas_origin.x + p.cx, canvas_origin.y + p.cy),
                      p.rad, IM_COL32(15, 15, 20, 180), 12, 1.0f);
        // EKCHOUS layer 2: at-rest marker — small white dot at the centre.
        if (p.at_rest) {
            dl->AddCircleFilled(
                ImVec2(canvas_origin.x + p.cx, canvas_origin.y + p.cy),
                std::max(1.5f, p.rad * 0.35f),
                IM_COL32(245, 245, 250, 230), 8);
        }
    }

    // RMB line-drag preview: draw a green line while the user is dragging
    // RMB in line-place mode so they can see what they're about to commit.
    if (rmb_line_drag_active_) {
        dl->AddLine(
            ImVec2(canvas_origin.x + rmb_line_start_x_, canvas_origin.y + rmb_line_start_y_),
            ImVec2(canvas_origin.x + mx_canvas,         canvas_origin.y + my_canvas),
            IM_COL32(140, 230, 140, 220), obs_line_thickness_ * 2.0f);
    }

    // Drag-box selection rectangle while held + halo around already-
    // selected particles.
    if (sel_drag_active_) {
        const float xmin = std::min(sel_start_x_, mx_canvas);
        const float xmax = std::max(sel_start_x_, mx_canvas);
        const float ymin = std::min(sel_start_y_, my_canvas);
        const float ymax = std::max(sel_start_y_, my_canvas);
        dl->AddRectFilled(
            ImVec2(canvas_origin.x + xmin, canvas_origin.y + ymin),
            ImVec2(canvas_origin.x + xmax, canvas_origin.y + ymax),
            IM_COL32(255, 220, 100, 32));
        dl->AddRect(
            ImVec2(canvas_origin.x + xmin, canvas_origin.y + ymin),
            ImVec2(canvas_origin.x + xmax, canvas_origin.y + ymax),
            IM_COL32(255, 220, 100, 220), 0.0f, 0, 1.5f);
    }
    if (!selected_particles_.empty()) {
        const auto& sparts = physics_.particles();
        const int n_parts = static_cast<int>(sparts.size());
        for (int idx : selected_particles_) {
            if (idx < 0 || idx >= n_parts) continue;
            const auto& p = sparts[idx];
            dl->AddCircle(
                ImVec2(canvas_origin.x + p.cx, canvas_origin.y + p.cy),
                p.rad + 3.0f, IM_COL32(255, 240, 120, 220), 16, 2.0f);
        }
    }

    // RMB delete-particle preview: when RMB action is 7, highlight the
    // particle that would be removed if the user clicks right now.
    if (rmb_action_idx_ == 7 && canvas_hovered && !rmb_held) {
        const int hover_idx = pick_particle(mx_canvas, my_canvas);
        if (hover_idx >= 0 && hover_idx < static_cast<int>(physics_.particles().size())) {
            const auto& hp = physics_.particles()[hover_idx];
            dl->AddCircle(ImVec2(canvas_origin.x + hp.cx, canvas_origin.y + hp.cy),
                          hp.rad + 4.0f, IM_COL32(255, 80, 80, 230), 16, 2.0f);
        }
    }

    // RMB delete-spring preview: when RMB action is 8, highlight the
    // spring under the cursor in red so the user sees what would be cut.
    if (rmb_action_idx_ == 8 && canvas_hovered && !rmb_held) {
        const int sidx = pick_spring(mx_canvas, my_canvas);
        if (sidx >= 0 && sidx < static_cast<int>(physics_.springs().size())) {
            const auto& s = physics_.springs()[sidx];
            const auto& pa = physics_.particles()[s.a_idx];
            const auto& pb = physics_.particles()[s.b_idx];
            dl->AddLine(ImVec2(canvas_origin.x + pa.cx, canvas_origin.y + pa.cy),
                        ImVec2(canvas_origin.x + pb.cx, canvas_origin.y + pb.cy),
                        IM_COL32(255, 80, 80, 230), 2.5f);
        }
    }

    // RMB spring-drag preview: highlight the start particle + draw a yellow
    // dashed line from it to the cursor (or to the snap target if one is
    // within pick_radius_).
    if (rmb_spring_drag_active_ && rmb_spring_start_idx_ >= 0 &&
        rmb_spring_start_idx_ < static_cast<int>(physics_.particles().size())) {
        const auto& sp = physics_.particles()[rmb_spring_start_idx_];
        const int hover_idx = pick_particle(mx_canvas, my_canvas);
        float end_x = mx_canvas;
        float end_y = my_canvas;
        if (hover_idx >= 0 && hover_idx != rmb_spring_start_idx_) {
            const auto& ep = physics_.particles()[hover_idx];
            end_x = ep.cx;
            end_y = ep.cy;
            // Glow over the snap target.
            dl->AddCircle(ImVec2(canvas_origin.x + ep.cx, canvas_origin.y + ep.cy),
                          ep.rad + 4.0f, IM_COL32(255, 240, 120, 230), 16, 2.0f);
        }
        // Glow over the start particle.
        dl->AddCircle(ImVec2(canvas_origin.x + sp.cx, canvas_origin.y + sp.cy),
                      sp.rad + 4.0f, IM_COL32(255, 240, 120, 230), 16, 2.0f);
        dl->AddLine(ImVec2(canvas_origin.x + sp.cx, canvas_origin.y + sp.cy),
                    ImVec2(canvas_origin.x + end_x, canvas_origin.y + end_y),
                    IM_COL32(255, 220, 100, 220), 1.5f);
    }

    // Cut-tool overlay: draw the current frame's cut segment while the user
    // is holding Alt+LMB so the slice is visible.
    if (lmb_held && alt_held && prev_mouse_canvas_x_ >= 0.0f) {
        dl->AddLine(
            ImVec2(canvas_origin.x + prev_mouse_canvas_x_, canvas_origin.y + prev_mouse_canvas_y_),
            ImVec2(canvas_origin.x + mx_canvas,            canvas_origin.y + my_canvas),
            IM_COL32(255, 80, 80, 220), 2.0f);
    }

    // Force-gun overlay: ring + push/pull indicator while LMB held.
    if (lmb_held && use_force_gun_ && !shift_held && !ctrl_held && !alt_held) {
        const ImU32 col = force_gun_attract_
            ? IM_COL32(120, 230, 255, 200)
            : IM_COL32(255, 150,  80, 200);
        dl->AddCircle(ImVec2(canvas_origin.x + mx_canvas, canvas_origin.y + my_canvas),
                      force_gun_radius_, col, 32, 2.0f);
    }

    ImGui::End();

    ImGui::Render();
    ImGui_ImplOpenGL3_RenderDrawData(ImGui::GetDrawData());
#endif
}

} // namespace ekchous
