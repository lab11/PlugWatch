/**
 * Cloud Interface.
 */

#pragma once

#include <Particle.h>

#define CHARGE_STATE_EVENT "c"
#define HEARTBEAT_EVENT "h"
#define IMU_EVENT "i"
#define IMU_ERROR_EVENT "!i"
#define SD_ERROR_EVENT "!s"
#define SD_READ_EVENT "l"

class Cloud {
public:
  static void setup();
  static void Publish(String tag, String message);
};
