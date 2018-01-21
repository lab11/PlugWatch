#pragma once

#include <Particle.h>

#include "FileLog.h"
#include "SDCard.h"
#include "Subsystem.h"

class ChargeState: public PeriodicSubsystem {
  typedef PeriodicSubsystem super;

  String message;

public:
  ChargeState(SDCard &sd, int* frequency) :
    PeriodicSubsystem(sd, "charge_state_log", frequency) {}

  static const int DEFAULT_FREQ = 1000 * 60 * 15;  // 15 min

  const String CHARGE_STATE_BATTERY = "b";
  const String CHARGE_STATE_WALL = "w";

  void setup();

private:
  void send();
  void periodic();
  void timerCallback();
  String cloudFunctionName() { return "cs"; }
};
