#pragma once

// Save / load scene state to a plain-text file. Line-oriented, easy to
// hand-edit. Captures the bits of scene state that aren't trivially
// re-derivable: physics params, static obstacles, force generators, flow
// emitters, and a snapshot of the live particle + spring vectors.
//
// Not saved (deliberately):
//   - Flow particle dust (transient visualization)
//   - Fluid density / velocity grids (large, dynamic)
//   - Flow field cell grid (user can re-set or paint)
//
// Spring references survive a round-trip because particles are loaded in
// the same order they were saved, so indices match.

#include "engine/sim/softbody/FlowField.h"
#include "engine/sim/softbody/FlowParticles.h"
#include "engine/sim/softbody/Physics.h"
#include <fstream>
#include <sstream>
#include <string>

namespace ekchous::softbody {

inline bool save_scene(const std::string& path,
                       const Physics& physics,
                       const FlowParticleSystem& flow_particles,
                       const FlowField& /*flow_field*/) {
    std::ofstream out(path);
    if (!out) return false;
    out << "# PixelFlow softbody scene\n";
    out << "version 1\n";

    out << "physics_gravity "
        << physics.params.gravity_x << " " << physics.params.gravity_y << "\n";
    out << "physics_iters "
        << physics.params.iterations_springs << " "
        << physics.params.iterations_collisions << "\n";
    out << "physics_bounds "
        << physics.params.bounds_xmin << " " << physics.params.bounds_ymin << " "
        << physics.params.bounds_xmax << " " << physics.params.bounds_ymax << "\n";
    out << "physics_damp "
        << physics.particle_damp.bounds << " "
        << physics.particle_damp.collision << " "
        << physics.particle_damp.velocity << "\n";
    out << "physics_tear " << physics.params.spring_tear_threshold << "\n";

    for (const auto& d : physics.static_disks()) {
        out << "static_disk " << d.disk.cx << " " << d.disk.cy << " "
            << d.disk.radius << " " << d.damp << "\n";
    }
    for (const auto& b : physics.static_boxes()) {
        out << "static_box " << b.aabb.min_x << " " << b.aabb.min_y << " "
            << b.aabb.max_x << " " << b.aabb.max_y << " " << b.damp << "\n";
    }
    for (const auto& l : physics.static_lines()) {
        out << "static_line " << l.ax << " " << l.ay << " " << l.bx << " "
            << l.by << " " << l.thickness << " " << l.damp << "\n";
    }
    for (const auto& g : physics.point_gravities()) {
        out << "point_gravity " << g.cx << " " << g.cy << " "
            << g.strength << " " << g.max_radius << "\n";
    }
    for (const auto& d : physics.drag_fields()) {
        out << "drag_field " << d.aabb.min_x << " " << d.aabb.min_y << " "
            << d.aabb.max_x << " " << d.aabb.max_y << " " << d.drag << "\n";
    }
    for (const auto& e : flow_particles.emitters()) {
        out << "flow_emitter " << e.x << " " << e.y << " " << e.per_frame << " "
            << e.vx_jitter << " " << e.vy_jitter << " "
            << (e.enabled ? 1 : 0) << "\n";
    }

    out << "particle_count " << physics.particles().size() << "\n";
    for (const auto& p : physics.particles()) {
        out << "p " << p.cx << " " << p.cy << " " << p.px << " " << p.py << " "
            << p.rad << " " << p.rad_collision << " " << p.mass << " "
            << p.collision_group << " "
            << (p.enable_forces ? 1 : 0) << " "
            << (p.enable_springs ? 1 : 0) << " "
            << (p.enable_collisions ? 1 : 0) << " "
            << p.user_data << " "
            << static_cast<int>(p.element_id) << " "
            << static_cast<int>(p.template_id) << "\n";
    }
    out << "spring_count " << physics.springs().size() << "\n";
    for (const auto& s : physics.springs()) {
        out << "s " << s.a_idx << " " << s.b_idx << " " << s.dd_rest << " "
            << s.damp_inc << " " << s.damp_dec << " "
            << (s.enabled ? 1 : 0) << " "
            << static_cast<int>(s.type) << "\n";
    }
    out << "end\n";
    return out.good();
}

inline bool load_scene(const std::string& path,
                       Physics& physics,
                       FlowParticleSystem& flow_particles,
                       FlowField& /*flow_field*/) {
    std::ifstream in(path);
    if (!in) return false;

    physics.reset();
    flow_particles.clear_emitters();

    std::string line;
    while (std::getline(in, line)) {
        if (line.empty() || line[0] == '#') continue;
        std::istringstream iss(line);
        std::string cmd;
        if (!(iss >> cmd)) continue;

        if (cmd == "version" || cmd == "end" ||
            cmd == "particle_count" || cmd == "spring_count") {
            // metadata-only lines
        } else if (cmd == "physics_gravity") {
            iss >> physics.params.gravity_x >> physics.params.gravity_y;
        } else if (cmd == "physics_iters") {
            iss >> physics.params.iterations_springs
                >> physics.params.iterations_collisions;
        } else if (cmd == "physics_bounds") {
            iss >> physics.params.bounds_xmin >> physics.params.bounds_ymin
                >> physics.params.bounds_xmax >> physics.params.bounds_ymax;
        } else if (cmd == "physics_damp") {
            iss >> physics.particle_damp.bounds
                >> physics.particle_damp.collision
                >> physics.particle_damp.velocity;
        } else if (cmd == "physics_tear") {
            iss >> physics.params.spring_tear_threshold;
        } else if (cmd == "static_disk") {
            float x, y, r, damp;
            iss >> x >> y >> r >> damp;
            physics.add_static_disk(x, y, r, damp);
        } else if (cmd == "static_box") {
            float xmin, ymin, xmax, ymax, damp;
            iss >> xmin >> ymin >> xmax >> ymax >> damp;
            physics.add_static_box(xmin, ymin, xmax, ymax, damp);
        } else if (cmd == "static_line") {
            float ax, ay, bx, by, thickness, damp;
            iss >> ax >> ay >> bx >> by >> thickness >> damp;
            physics.add_static_line(ax, ay, bx, by, thickness, damp);
        } else if (cmd == "point_gravity") {
            PointGravity g;
            iss >> g.cx >> g.cy >> g.strength >> g.max_radius;
            physics.add_point_gravity(g);
        } else if (cmd == "drag_field") {
            DragField d;
            iss >> d.aabb.min_x >> d.aabb.min_y
                >> d.aabb.max_x >> d.aabb.max_y >> d.drag;
            physics.add_drag_field(d);
        } else if (cmd == "flow_emitter") {
            FlowEmitter e;
            int enabled;
            iss >> e.x >> e.y >> e.per_frame
                >> e.vx_jitter >> e.vy_jitter >> enabled;
            e.enabled = (enabled != 0);
            flow_particles.add_emitter(e);
        } else if (cmd == "p") {
            Particle p;
            int ef, es, ec;
            iss >> p.cx >> p.cy >> p.px >> p.py
                >> p.rad >> p.rad_collision >> p.mass
                >> p.collision_group >> ef >> es >> ec;
            p.enable_forces     = (ef != 0);
            p.enable_springs    = (es != 0);
            p.enable_collisions = (ec != 0);
            // user_data is optional (back-compat with older saves that lacked it).
            unsigned int ud = 0;
            if (iss >> ud) p.user_data = ud;
            // element_id likewise optional.
            int el = 0;
            if (iss >> el) p.element_id = static_cast<core::u8>(el);
            // template_id optional.
            int tpl = 0;
            if (iss >> tpl) p.template_id = static_cast<core::u8>(tpl);
            physics.add_particle(p);
        } else if (cmd == "s") {
            int a_idx, b_idx, en, type;
            float rest, damp_inc, damp_dec;
            iss >> a_idx >> b_idx >> rest >> damp_inc >> damp_dec >> en >> type;
            const int s_idx = physics.add_spring(a_idx, b_idx, rest,
                                                  static_cast<SpringType>(type));
            auto& sp = physics.springs()[s_idx];
            sp.damp_inc = damp_inc;
            sp.damp_dec = damp_dec;
            sp.enabled  = (en != 0);
        }
    }
    return true;
}

} // namespace ekchous::softbody
