#pragma once

#include <Particle.h>

#include "FileLog.h"
#include "Subsystem.h"

class Heartbeat: public Subsystem {
  typedef Subsystem super;

  int* count;

public:
  static const int DEFAULT_FREQ = 1000 * 60 * 15;  // 15 min

  Heartbeat(int* count) : Subsystem(),
    count { count } { *count = 0; }

  LoopStatus loop();
  String getResult();
};
