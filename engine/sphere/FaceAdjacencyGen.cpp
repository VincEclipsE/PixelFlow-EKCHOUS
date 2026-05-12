// Build-time tool: generates assets/sphere/face_adjacency.bin from the
// canonical table in CubedSphere.cpp.
//
// Run by CMake's add_custom_command. The binary file is committed under
// .gitignore; regenerated on every build to ensure consistency.
//
// Usage: face_adjacency_gen <output-path>

#include "engine/sphere/CubedSphere.h"

#include <iostream>
#include <filesystem>

int main(int argc, char** argv) {
    if (argc != 2) {
        std::cerr << "usage: face_adjacency_gen <output-path>\n";
        return 2;
    }

    std::filesystem::path out = argv[1];
    std::filesystem::create_directories(out.parent_path());

    auto table = ekchous::sphere::FaceAdjacency::canonical();
    table.save_to_file(out.string());

    std::cout << "face_adjacency_gen: wrote 24 entries to " << out << "\n";
    return 0;
}
