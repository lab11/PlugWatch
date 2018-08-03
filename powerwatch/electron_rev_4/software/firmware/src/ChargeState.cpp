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

  uint8_t charge = powerCheck.getIsCharging();
  uint8_t power = powerCheck.getHasPower();
  uint8_t both = (charge << 1) | power;

  String power_stats = String(state_of_charge) + String(MINOR_DLIM)
  + String(vcell) + String(MINOR_DLIM)
  + String(both) + String(MINOR_DLIM)
  + String(powerCheck.lastUnplugMillis) + String(MINOR_DLIM)
  + String(powerCheck.lastPlugMillis) + String(MINOR_DLIM)
  + String(powerCheck.getVoltage()) + String(MINOR_DLIM)
  + String(powerCheck.getLCycles()) + String(MINOR_DLIM)
  + String(powerCheck.getNCycles());

  result = power_stats;

  powerCheck.enableCharging();
  powerCheck.setChargeCurrent();
  powerCheck.lowerChargeVoltage();

  return FinishedSuccess;
}

String ChargeState::getResult() {
  return result;
}
