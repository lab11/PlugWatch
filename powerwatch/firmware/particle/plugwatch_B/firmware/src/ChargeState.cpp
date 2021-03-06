#include <Particle.h>

#include "ChargeState.h"
#include "Cloud.h"
#include "FileLog.h"
#include "PowerCheck.h"
#include "Subsystem.h"
#include "firmware.h"

PowerCheck powerCheck;

void ChargeState::setup() {
  super::setup();

  powerCheck.setup();
}

void ChargeState::send() {
    String power_stats = String(FuelGauge().getSoC()) + String("|") + String(FuelGauge().getVCell()) + String("|") + String(powerCheck.getIsCharging());
    Cloud::Publish(CHARGE_STATE_EVENT, message + String("|") + power_stats);
    log.append(power_stats);
}

void ChargeState::periodic(bool force) {
  static bool last_charge_state = false;
  bool charge_state = powerCheck.getIsCharging();

  if (charge_state == true) {
    digitalWrite(debug_led_1, LOW);
    message = CHARGE_STATE_WALL;
  } else {
    digitalWrite(debug_led_1, HIGH);
    message = CHARGE_STATE_BATTERY;
  }

  if (charge_state != last_charge_state) {
    log.appendFromISR("Charge state change to " + String(charge_state));
    last_charge_state = charge_state;
    send();
  }
  if (force) {
    send();
  }
}
