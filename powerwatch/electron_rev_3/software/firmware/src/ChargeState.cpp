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
  // XXXX we don't actually set message anywhere currently
  // return(message + String(DLIM) + power_stats);

  result = power_stats;
}

String ChargeState::getResult() {
  return result;
}
