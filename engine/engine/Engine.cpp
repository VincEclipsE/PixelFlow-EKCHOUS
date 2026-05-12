#include "engine/engine/Engine.h"
#include "engine/core/Logger.h"
#include "engine/sphere/CubedSphere.h"

// GLFW is included; ImGui is optional. The Day-One demo can also run headless
// for the replay-test, which never touches GLFW.

#include <GLFW/glfw3.h>

// ImGui includes (only when not headless).
#ifndef EKCHOUS_NO_IMGUI
#include <imgui.h>
#include <imgui_impl_glfw.h>
#include <imgui_impl_opengl3.h>
#endif

#include <chrono>
#include <thread>

namespace ekchous {

static void glfw_error_callback(int err, const char* msg) {
    LOG_ERROR("GLFW error {}: {}", err, msg);
}

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
    LOG_INFO("EKCHOUS engine starting (world_seed={}, sim={}Hz, render={}Hz, headless={})",
             cfg_.world_seed, cfg_.sim_hz, cfg_.render_hz, cfg_.headless);

    // Verify face-adjacency LUT is accessible (Day-One unit test target).
    try {
        std::string lut_path = std::string(EKCHOUS_ASSET_DIR) + "/sphere/face_adjacency.bin";
        auto lut = sphere::FaceAdjacency::load_from_file(lut_path);
        LOG_INFO("Loaded cubed-sphere face-adjacency LUT ({} entries) from {}",
                 lut.entries().size(), lut_path);
    } catch (const std::exception& e) {
        LOG_WARN("Could not load face-adjacency LUT: {}. (Day-One demo continues; full sim needs it.)", e.what());
    }

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

    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
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
    glfwSwapInterval(1); // vsync

    // TODO_PIXELFLOW_PORT("dwgl init pattern"): glad loader would init here.
    LOG_INFO("GL context created (GL 4.3 core profile requested)");
    return true;
}

bool Engine::init_imgui() {
#ifndef EKCHOUS_NO_IMGUI
    IMGUI_CHECKVERSION();
    ImGui::CreateContext();
    ImGui::StyleColorsDark();
    ImGui_ImplGlfw_InitForOpenGL(window_, true);
    ImGui_ImplOpenGL3_Init("#version 430");
    imgui_initialized_ = true;
    LOG_INFO("ImGui initialized");
#endif
    return true;
}

bool Engine::init_sim() {
    demo_ = std::make_unique<sim::FallingSandDemo>(cfg_.world_seed);
    demo_->reset();
    LOG_INFO("FallingSandDemo initialized (CPU reference; passes 11+12+14r+19)");
    return true;
}

void Engine::sim_tick() {
    demo_->tick(tick_count_);

    // Golden hash: xxHash3 over the entire pixel grid SSBO contents.
    const auto& bytes = demo_->grid_bytes();
    core::u64 h = determinism::hash_bytes(bytes.data(), bytes.size());
    golden_log_.record(h);

    ++tick_count_;
}

void Engine::render_frame() {
    if (cfg_.headless) return;

#ifndef EKCHOUS_NO_IMGUI
    ImGui_ImplOpenGL3_NewFrame();
    ImGui_ImplGlfw_NewFrame();
    ImGui::NewFrame();

    // Debug HUD.
    ImGui::Begin("EKCHOUS Engine");
    ImGui::Text("tick: %llu", static_cast<unsigned long long>(tick_count_));
    ImGui::Text("sim: %d Hz   render: %d Hz", cfg_.sim_hz, cfg_.render_hz);
    if (!golden_log_.hashes().empty()) {
        ImGui::Text("latest golden hash: %016llx",
                    static_cast<unsigned long long>(golden_log_.hashes().back()));
    }
    ImGui::Text("in_flux: %zu", demo_->in_flux_count());
    ImGui::Text("settled: %zu", demo_->settled_count());
    ImGui::End();

    ImGui::Render();
    ImGui_ImplOpenGL3_RenderDrawData(ImGui::GetDrawData());
#endif

    glfwSwapBuffers(window_);
}

int Engine::run() {
    if (cfg_.replay_ticks > 0) {
        return run_replay_test();
    }

    const double sim_period = 1.0 / double(cfg_.sim_hz);
    auto t_start = std::chrono::steady_clock::now();
    double t_accumulator = 0.0;
    auto t_prev = std::chrono::steady_clock::now();

    while (!glfwWindowShouldClose(window_)) {
        glfwPollEvents();
        auto t_now = std::chrono::steady_clock::now();
        double dt = std::chrono::duration<double>(t_now - t_prev).count();
        t_prev = t_now;
        t_accumulator += dt;

        // Sim tick at fixed rate; multiple ticks per render if needed.
        while (t_accumulator >= sim_period) {
            sim_tick();
            t_accumulator -= sim_period;
        }

        render_frame();
    }
    return 0;
}

int Engine::run_replay_test() {
    LOG_INFO("=== DETERMINISM REPLAY TEST: {} ticks ===", cfg_.replay_ticks);

    // First run.
    LOG_INFO("First run...");
    demo_->reset();
    golden_log_ = {};
    tick_count_ = 0;
    for (int i = 0; i < cfg_.replay_ticks; ++i) {
        sim_tick();
    }
    auto first_log = golden_log_;
    LOG_INFO("  First run: {} ticks, final hash {:016x}",
             first_log.size(), first_log.hashes().back());

    // Second run.
    LOG_INFO("Second run...");
    demo_->reset();
    golden_log_ = {};
    tick_count_ = 0;
    for (int i = 0; i < cfg_.replay_ticks; ++i) {
        sim_tick();
    }
    auto second_log = golden_log_;
    LOG_INFO("  Second run: {} ticks, final hash {:016x}",
             second_log.size(), second_log.hashes().back());

    // Compare.
    int divergence = determinism::GoldenLog::compare(first_log, second_log);
    if (divergence == -1) {
        LOG_INFO("=== DETERMINISM REPLAY: PASS ({} ticks bit-identical) ===", cfg_.replay_ticks);
        return 0;
    } else {
        LOG_ERROR("=== DETERMINISM REPLAY: FAIL at tick {} ===", divergence);
        LOG_ERROR("  first  hash:  {:016x}", first_log.hashes()[divergence]);
        LOG_ERROR("  second hash:  {:016x}", second_log.hashes()[divergence]);
        return 1;
    }
}

} // namespace ekchous
