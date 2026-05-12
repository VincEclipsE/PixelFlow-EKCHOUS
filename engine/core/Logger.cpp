#include "engine/core/Logger.h"

namespace ekchous::core {

std::shared_ptr<spdlog::logger> Logger::s_logger;

void Logger::init() {
    if (s_logger) return;
    s_logger = spdlog::stdout_color_mt("ekchous");
    s_logger->set_level(spdlog::level::trace);
    s_logger->set_pattern("[%H:%M:%S.%e] [%n] [%^%l%$] %v");
}

std::shared_ptr<spdlog::logger>& Logger::get() {
    if (!s_logger) init();
    return s_logger;
}

} // namespace ekchous::core
