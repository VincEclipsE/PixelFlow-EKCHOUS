#pragma once

// Top-level engine orchestrator.
// Port target: src/com/thomasdiewald/pixelflow/java/DwPixelFlow.java
//
// Responsibilities:
//   - GLFW window + GL 4.3 core context creation
//   - ImGui boot + per-frame begin/end
//   - 15 Hz sim tick scheduler with 60 Hz render loop
//   - Run the falling-sand demo (Day-One)
//   - Drive the determinism golden-hash log

#include "engine/core/Types.h"
#include "engine/determinism/GoldenHash.h"
#include "engine/sim/passes/FallingSandDemo.h"
#include <memory>
#include <string>

struct GLFWwindow;

namespace ekchous {

struct EngineConfig {
    int window_width = 1920;
    int window_height = 1080;
    std::string window_title = "EKCHOUS Pixel Physics Engine — Day-One";
    int sim_hz = 15;
    int render_hz = 60;
    core::u64 world_seed = 42;
    bool headless = false;            // If true, don't create a window (replay-test mode).
    int replay_ticks = 0;             // If >0, run N ticks and exit with golden-hash verification.
};

class Engine {
public:
    Engine() = default;
    ~Engine();

    // Boot subsystems. Returns false on fatal failure.
    bool init(const EngineConfig& cfg);

    // Run the main loop until the window closes (or replay-test completes).
    // Returns the OS-level exit code (0 = success, non-zero = failure).
    int run();

    const determinism::GoldenLog& golden_log() const noexcept { return golden_log_; }

private:
    bool init_window();
    bool init_imgui();
    bool init_sim();

    // Run one sim tick (15 Hz). Pure CPU for Day-One; hashed into golden_log_.
    void sim_tick();

    // Run one render frame (60 Hz).
    void render_frame();

    // Sub-mode: headless replay test. Runs `cfg_.replay_ticks` ticks twice
    // and asserts identical golden_log_ across the two runs.
    int run_replay_test();

    EngineConfig cfg_{};
    GLFWwindow* window_ = nullptr;
    bool imgui_initialized_ = false;
    core::u64 tick_count_ = 0;

    std::unique_ptr<sim::FallingSandDemo> demo_;
    determinism::GoldenLog golden_log_;
};

} // namespace ekchous
