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

void ChargeState::loop() {
  static bool last_charge_state = false;


  if (timer_flag) {
    timer_flag = false;
    bool charge_state = powerCheck.getIsCharging();
    // only report if the charge state has changed:
    if (charge_state != last_charge_state) {
      // XXXX was already commented out; pretty sure redundant with report
      // of powerCheck.getIsCharging()
      /*
      if (charge_state == true) {
        digitalWrite(debug_led_1, LOW);
        message = CHARGE_STATE_WALL;
      } else {
        digitalWrite(debug_led_1, HIGH);
        message = CHARGE_STATE_BATTERY;
      }
      */

      last_charge_state = charge_state;
      // XXXX Note: if we want to report on all sensors any time charge state
      // changes, we could set a global flag here that is handled in top-level
      // loop, which calls run(rChargeState) on all the classes

      log.append("Charge state change to " + String(charge_state));
      run(rPeriodic); // or rChargeState change?
    }
  }
}

String ChargeState::getReading() {
    String power_stats = String(FuelGauge().getSoC()) + String(DLIM)
      + String(FuelGauge().getVCell()) + String(DLIM)
      + String(powerCheck.getIsCharging());
    // XXXX we don't actually set message anywhere currently
    // return(message + String(DLIM) + power_stats);
    return(power_stats);
}
