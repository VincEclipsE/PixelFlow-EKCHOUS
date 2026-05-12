#pragma once

#include <spdlog/spdlog.h>
#include <spdlog/sinks/stdout_color_sinks.h>
#include <memory>

namespace ekchous::core {

class Logger {
public:
    static void init();
    static std::shared_ptr<spdlog::logger>& get();

private:
    static std::shared_ptr<spdlog::logger> s_logger;
};

} // namespace ekchous::core

// Convenience macros.
#define LOG_TRACE(...) ::ekchous::core::Logger::get()->trace(__VA_ARGS__)
#define LOG_DEBUG(...) ::ekchous::core::Logger::get()->debug(__VA_ARGS__)
#define LOG_INFO(...)  ::ekchous::core::Logger::get()->info(__VA_ARGS__)
#define LOG_WARN(...)  ::ekchous::core::Logger::get()->warn(__VA_ARGS__)
#define LOG_ERROR(...) ::ekchous::core::Logger::get()->error(__VA_ARGS__)
#define LOG_FATAL(...) ::ekchous::core::Logger::get()->critical(__VA_ARGS__)

// Stub markers from architecture doc; surface in grep audits.
#define TODO_CORPUS(doc_section) \
    do { LOG_WARN("TODO_CORPUS({}): not implemented in {}:{}", #doc_section, __FILE__, __LINE__); } while (0)
#define TODO_PIXELFLOW_PORT(java_path) \
    do { LOG_WARN("TODO_PIXELFLOW_PORT({}): not implemented in {}:{}", #java_path, __FILE__, __LINE__); } while (0)
