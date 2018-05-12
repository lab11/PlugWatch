#pragma once

#include <Particle.h>

#include "Subsystem.h"

#include "ISL29035.h"

class Light: public Subsystem {
  typedef Subsystem super;

  ISL29035 light_sensor;
  float* lux;

public:
  Light(float* lux) : Subsystem(),
    lux { lux }
    { light_sensor.init(); }
    
  String getResult();
};
