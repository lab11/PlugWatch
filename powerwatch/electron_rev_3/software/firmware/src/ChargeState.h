#pragma once

#include <Particle.h>

#include "FileLog.h"
#include "SDCard.h"
#include "Subsystem.h"

/* Checks charging state at `frequency`. Publishes every change. */
class ChargeState: public PeriodicSubsystem {
  typedef PeriodicSubsystem super;

  String message;

public:
  ChargeState(SDCard &sd, int* frequency) :
    PeriodicSubsystem(sd, "charge_state_log", frequency) {}

  static const int DEFAULT_FREQ = 1000 * 5;  // 5 seconds

  const String CHARGE_STATE_BATTERY = "b";
  const String CHARGE_STATE_WALL = "w";

  void setup();

private:
  void send();
  void sample();
  void periodic(bool force);
  String cloudFunctionName() { return "cs"; }
};
