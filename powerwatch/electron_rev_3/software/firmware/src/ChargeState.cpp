#include <Particle.h>

#include "ChargeState.h"
#include "PowerCheck.h"
#include "Subsystem.h"

PowerCheck powerCheck;

void ChargeState::setup() {
  super::setup();

  powerCheck.setup();
}

LoopStatus ChargeState::loop() {
  char state_of_charge[4];
  snprintf(state_of_charge, 4, "%d", (int)FuelGauge().getSoC());

  char vcell[5];
  snprintf(vcell, 5, "%0.2f", FuelGauge().getVCell());

  String power_stats = String(state_of_charge) + String(MINOR_DLIM)
  + String(vcell) + String(MINOR_DLIM)
  + String(powerCheck.getIsCharging()) + String(MINOR_DLIM)
  + String(powerCheck.getHasPower());

  result = power_stats;
  return FinishedSuccess;
}

String ChargeState::getResult() {
  return result;
}
