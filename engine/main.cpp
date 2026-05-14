// PixelFlow softbody engine — entry point.
//
// Usage:
//   ekchous              Open a window and run the softbody demo
//   ekchous --headless   Run 600 ticks then exit (smoke test)

#include "engine/engine/Engine.h"
#include "engine/core/Logger.h"
#include <cstdlib>
#include <string>

int main(int argc, char** argv) {
    ekchous::EngineConfig cfg;
    for (int i = 1; i < argc; ++i) {
        std::string a = argv[i];
        if (a == "--headless") {
            cfg.headless = true;
        } else if (a == "--help" || a == "-h") {
            std::printf("Usage: ekchous [--headless]\n");
            return 0;
        }
    }
    ekchous::Engine engine;
    if (!engine.init(cfg)) return 1;
    return engine.run();
}
