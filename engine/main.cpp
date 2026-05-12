// EKCHOUS Pixel Physics Engine — entry point.
//
// Usage:
//   ekchous                         Boot GLFW + ImGui + run interactively
//   ekchous --replay-test=N         Run N ticks twice headlessly; verify
//                                   bit-identical xxHash3 golden-state hashes
//   ekchous --headless              Run interactively without GLFW (logs only)

#include "engine/engine/Engine.h"
#include "engine/core/Logger.h"
#include <cstdlib>
#include <cstring>
#include <string>

int main(int argc, char** argv) {
    ekchous::EngineConfig cfg;

    for (int i = 1; i < argc; ++i) {
        std::string a = argv[i];
        if (a.rfind("--replay-test=", 0) == 0) {
            cfg.replay_ticks = std::atoi(a.c_str() + std::strlen("--replay-test="));
            cfg.headless = true;
        } else if (a == "--headless") {
            cfg.headless = true;
        } else if (a.rfind("--seed=", 0) == 0) {
            cfg.world_seed = std::strtoull(a.c_str() + std::strlen("--seed="), nullptr, 10);
        } else if (a == "--help" || a == "-h") {
            std::printf("Usage: ekchous [--replay-test=N] [--headless] [--seed=N]\n");
            return 0;
        }
    }

    ekchous::Engine engine;
    if (!engine.init(cfg)) {
        return 1;
    }
    return engine.run();
}
