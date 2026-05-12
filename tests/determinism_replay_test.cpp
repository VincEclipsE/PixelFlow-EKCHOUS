// Day-One acceptance test #4: 1000-tick falling-sand demo bit-identical replay.
//
// Equivalent to running `./build/ekchous --replay-test=1000` from the
// command line, but driven by Catch2 so it integrates with CTest.

#include <catch2/catch_test_macros.hpp>
#include "engine/sim/passes/FallingSandDemo.h"
#include "engine/determinism/GoldenHash.h"

using namespace ekchous;
using namespace ekchous::sim;
using namespace ekchous::determinism;

static GoldenLog run_replay(core::u64 seed, int ticks) {
    FallingSandDemo demo(seed);
    demo.reset();
    GoldenLog log;
    for (int i = 0; i < ticks; ++i) {
        demo.tick(static_cast<core::u64>(i));
        const auto& bytes = demo.grid_bytes();
        log.record(hash_bytes(bytes.data(), bytes.size()));
    }
    return log;
}

TEST_CASE("falling-sand demo: 100-tick replay is bit-identical", "[determinism]") {
    auto first  = run_replay(/*seed*/ 42, /*ticks*/ 100);
    auto second = run_replay(42, 100);

    int divergence = GoldenLog::compare(first, second);
    INFO("divergence tick = " << divergence);
    REQUIRE(divergence == -1);
}

TEST_CASE("falling-sand demo: 1000-tick replay is bit-identical", "[determinism]") {
    auto first  = run_replay(/*seed*/ 42, /*ticks*/ 1000);
    auto second = run_replay(42, 1000);

    int divergence = GoldenLog::compare(first, second);
    INFO("divergence tick = " << divergence);
    REQUIRE(divergence == -1);
}

TEST_CASE("different seeds produce different hash logs", "[determinism]") {
    auto seed_42 = run_replay(42, 100);
    auto seed_43 = run_replay(43, 100);

    // The two runs SHOULD diverge somewhere within the first 100 ticks because
    // the seed feeds RNG-driven sand emission. If they didn't diverge, our RNG
    // isn't seeded properly.
    int divergence = GoldenLog::compare(seed_42, seed_43);
    REQUIRE(divergence != -1);
}

TEST_CASE("empty demo has stable empty-grid hash", "[determinism]") {
    FallingSandDemo demo(0);
    demo.reset();
    const auto& bytes = demo.grid_bytes();
    core::u64 h1 = hash_bytes(bytes.data(), bytes.size());

    demo.reset();
    const auto& bytes2 = demo.grid_bytes();
    core::u64 h2 = hash_bytes(bytes2.data(), bytes2.size());

    REQUIRE(h1 == h2);
}
