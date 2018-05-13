#pragma once

#include <Particle.h>

#include "FileLog.h"
#include "Subsystem.h"

/* Checks charging state at `frequency`. Publishes every change. */
class ChargeState: public Subsystem {
  typedef Subsystem super;

  String result;

public:
  ChargeState();

  const String CHARGE_STATE_BATTERY = "b";
  const String CHARGE_STATE_WALL = "w";

  void setup();
  LoopStatus loop();
  String getResult();
};
