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
  String power_stats = String(FuelGauge().getSoC()) + String(DLIM)
  + String(FuelGauge().getVCell()) + String(DLIM)
  + String(powerCheck.getIsCharging());

  result = power_stats;
  return FinishedSuccess;
}

String ChargeState::getResult() {
  return result;
}
