// Banned-ops lint for compute shaders.
//
// Walks a directory of .comp files and rejects any occurrence of:
//   fma(   pow(   exp(   log(   sin(   cos(   tan(
//   rsqrt(   inversesqrt(
//   imageAtomicCAS(   atomicExchange(   atomicCompSwap(
//
// Render-side shaders (under shaders/glsl/render/ or post/) are NOT scanned;
// only the sim path requires the discipline.
//
// Build-integrated: CMake adds a custom target that runs this before linking
// the main executable. Non-zero exit aborts the build.
//
// Usage: banned_ops_lint <dir>
//   <dir> = directory of .comp files (e.g. shaders/glsl/sim)

#include <filesystem>
#include <fstream>
#include <iostream>
#include <regex>
#include <string>
#include <vector>

namespace fs = std::filesystem;

static const std::vector<std::pair<std::string, std::regex>> kBannedOps = {
    {"fma",                  std::regex(R"(\bfma\s*\()")},
    {"pow",                  std::regex(R"(\bpow\s*\()")},
    {"exp",                  std::regex(R"(\bexp\s*\()")},
    {"log",                  std::regex(R"(\blog\s*\()")},
    {"sin",                  std::regex(R"(\bsin\s*\()")},
    {"cos",                  std::regex(R"(\bcos\s*\()")},
    {"tan",                  std::regex(R"(\btan\s*\()")},
    {"rsqrt",                std::regex(R"(\brsqrt\s*\()")},
    {"inversesqrt",          std::regex(R"(\binversesqrt\s*\()")},
    {"imageAtomicCAS",       std::regex(R"(\bimageAtomicCAS\s*\()")},
    {"atomicExchange",       std::regex(R"(\batomicExchange\s*\()")},
    {"atomicCompSwap",       std::regex(R"(\batomicCompSwap\s*\()")},
};

static int scan_file(const fs::path& path) {
    std::ifstream f(path);
    if (!f) {
        std::cerr << "banned_ops_lint: could not open " << path << "\n";
        return 1;
    }
    int violations = 0;
    std::string line;
    int line_num = 0;
    while (std::getline(f, line)) {
        ++line_num;
        // Skip comments (single-line only; not robust against block comments).
        auto comment_start = line.find("//");
        std::string scan_line = (comment_start == std::string::npos)
            ? line
            : line.substr(0, comment_start);

        for (const auto& [name, pattern] : kBannedOps) {
            if (std::regex_search(scan_line, pattern)) {
                std::cerr << "BANNED OP '" << name << "' at "
                          << path.string() << ":" << line_num << ": " << line << "\n";
                ++violations;
            }
        }
    }
    return violations;
}

int main(int argc, char** argv) {
    if (argc != 2) {
        std::cerr << "usage: banned_ops_lint <shader-dir>\n";
        return 2;
    }
    fs::path root = argv[1];
    if (!fs::is_directory(root)) {
        // No shaders yet — not an error.
        std::cout << "banned_ops_lint: no shader directory at " << root << " (ok for Day-One)\n";
        return 0;
    }

    int total_violations = 0;
    int files_scanned = 0;
    for (auto& entry : fs::recursive_directory_iterator(root)) {
        if (!entry.is_regular_file()) continue;
        if (entry.path().extension() != ".comp") continue;
        ++files_scanned;
        total_violations += scan_file(entry.path());
    }

    if (total_violations > 0) {
        std::cerr << "banned_ops_lint: " << total_violations
                  << " violations across " << files_scanned << " file(s). Build aborted.\n";
        return 1;
    }
    std::cout << "banned_ops_lint: ok (" << files_scanned << " .comp file(s) clean)\n";
    return 0;
}
