#pragma once

#include <Particle.h>

#include "Subsystem.h"

class Gps: public Subsystem {
  typedef Subsystem super;

  String result;

public:
  // Arduino setup
  void setup();
  LoopStatus loop();
  String getResult();
};
