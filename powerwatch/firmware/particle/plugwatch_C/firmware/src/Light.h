#pragma once

#include <Particle.h>

#include "Subsystem.h"

#include "ISL29035.h"

class Light: public Subsystem {
  typedef Subsystem super;

  ISL29035 light_sensor;
  float lux;

  String result;

public:
  void setup();
  LoopStatus loop();
  String getResult();
};
