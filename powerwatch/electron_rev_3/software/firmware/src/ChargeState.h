#pragma once

#include <Particle.h>

#include "FileLog.h"
#include "SDCard.h"
#include "Subsystem2.h"

/* Checks charging state at `frequency`. Publishes every change. */
class ChargeState: public PeriodicSubsystem2 {
  typedef PeriodicSubsystem2 super;

  // XXXX this is redundant
  // String message;

public:
  ChargeState(SDCard &sd, int* frequency) :
    PeriodicSubsystem2(sd, "charge_state_log", frequency)
    { eventTag = CHARGE_STATE_EVENT; }

  static const int DEFAULT_FREQ = 1000 * 5;  // 5 seconds

  const String CHARGE_STATE_BATTERY = "b";
  const String CHARGE_STATE_WALL = "w";

  void setup();
  void loop();
  String getReading();

private:
  void send();
  void periodic(bool force);
  String cloudFunctionName() { return "cs"; }
};
