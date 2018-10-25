#pragma once

#include <Particle.h>

#include "ISL29035.h"
#include "Subsystem.h"

class Audio: public Subsystem {
  typedef Subsystem super;

  ISL29035 audio_sensor;
  float* hz_and_mag;

public:
  Audio(float* hz_and_mag) :
    hz_and_mag { hz_and_mag }
    { audio_sensor.init(); }
 
  void setup();
  LoopStatus loop();
  String getResult();
};
