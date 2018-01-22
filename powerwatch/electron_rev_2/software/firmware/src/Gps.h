#pragma once

#include <Particle.h>

#include "Subsystem.h"

class Gps: public PeriodicSubsystem {
  typedef PeriodicSubsystem super;

public:
  static const int DEFAULT_FREQ = 1000 * 60 * 10;  // 10 min

  Gps(SDCard &sd, int* frequency) :
    PeriodicSubsystem(sd, "gps_log", frequency) {}

  // Arduino setup
  void setup();

private:
  // Called from Arduino loop, but only at `frequency` rate rather than every loop
  void periodic();

  String cloudFunctionName() { return "gps"; }
};
