/**
 * Cloud Interface.
 */

#pragma once

#include <Particle.h>

#define HEARTBEAT_EVENT "h"
#define SD_ERROR_EVENT "f"
#define SD_READ_EVENT "l"

void publish_wrapper(String tag, String message);
