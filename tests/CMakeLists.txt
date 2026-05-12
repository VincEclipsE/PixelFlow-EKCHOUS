# Tests for the EKCHOUS engine.

# Catch2 main
add_executable(sphere_seam_test sphere_seam_test.cpp)
target_link_libraries(sphere_seam_test PRIVATE
    ekchous_engine
    Catch2::Catch2WithMain
)
add_test(NAME sphere_seam_test COMMAND sphere_seam_test)

add_executable(determinism_replay_test determinism_replay_test.cpp)
target_link_libraries(determinism_replay_test PRIVATE
    ekchous_engine
    Catch2::Catch2WithMain
)
add_test(NAME determinism_replay_test COMMAND determinism_replay_test)
