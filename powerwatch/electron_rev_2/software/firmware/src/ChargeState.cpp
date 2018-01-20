#include <Particle.h>

#include "ChargeState.h"
#include "Cloud.h"
#include "FileLog.h"
#include "PowerCheck.h"
#include "firmware.h"

retained int ChargeState::frequency = ChargeState::DEFAULT_FREQ;

PowerCheck powerCheck;

void ChargeState::setup() {
  timer.start();
  Particle.function("cs", &ChargeState::cloudCommand, this);
  powerCheck.setup();

  log.append("ChargeState setup complete. Initial frequency " + String(frequency));
}

void ChargeState::send() {
    String power_stats = String(FuelGauge().getSoC()) + String("|") + String(FuelGauge().getVCell()) + String("|") + String(powerCheck.getIsCharging());
    Cloud::Publish(CHARGE_STATE_EVENT, message + String("|") + power_stats);
    log.append(power_stats);
}

void ChargeState::loop() {
  if (timer_flag) {
    timer_flag = false;

    log.append("Timer event.");
    send();
  }
}

void ChargeState::timerCallback() {
  static bool last_charge_state = false;
  bool charge_state = powerCheck.getIsCharging();

  if (charge_state == true) {
    digitalWrite(debug_led_2, LOW);
    message = CHARGE_STATE_WALL;
  } else {
    digitalWrite(debug_led_2, HIGH);
    message = CHARGE_STATE_BATTERY;
  }

  if (charge_state != last_charge_state) {
    log.appendFromISR("Charge state change to " + String(charge_state));
    timer_flag = true;
    last_charge_state = charge_state;
  }
}

int ChargeState::setFrequencyFromISR(int new_frequency) {
  if ((new_frequency < MIN_FREQ) || (new_frequency > MAX_FREQ)) {
    log.appendFromISR("Error updating frequency. Got: " + new_frequency);
    return -1;
  }

  frequency = new_frequency;
  timer.changePeriodFromISR(frequency);
  timer.resetFromISR();

  log.appendFromISR("Set frequency to " + String(frequency));
  return 0;
}

int ChargeState::cloudCommand(String command) {
  errno = 0;
  int new_frequency = strtol(command.c_str(), NULL, 10);
  if (errno != 0) {
    log.appendFromISR("Error updating frequency. Got: " + frequency);
    return -1;
  }
  return setFrequencyFromISR(new_frequency);
}
