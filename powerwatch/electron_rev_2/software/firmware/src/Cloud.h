/**
 * Cloud Interface.
 */

#pragma once

#include <Particle.h>

#define HEARTBEAT_EVENT "h"
#define SD_ERROR_EVENT "f"
#define SD_READ_EVENT "l"

class Cloud {
public:
  static void setup();
  static void Publish(String tag, String message);
};
