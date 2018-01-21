#include <Particle.h>

#include "Subsystem.h"

void Subsystem::setup() {
  Particle.function(cloudFunctionName(), &Subsystem::cloudCommand, this);
}

void Subsystem::loop() {
}



void PeriodicSubsystem::loop() {
  super::loop();
  if (timer_flag) {
    timer_flag = false;

    log.append("Timer event.");
    periodic();
  }
}

void PeriodicSubsystem::setup() {
  super::setup();
  timer.start();
  log.append("Periodic setup complete. Initial frequency " + String(*frequency));
}

void PeriodicSubsystem::timerCallback() {
  timer_flag = true;
}

int PeriodicSubsystem::setFrequencyFromISR(int new_frequency) {
  if ((new_frequency < MIN_FREQ) || (new_frequency > MAX_FREQ)) {
    log.appendFromISR("Error updating frequency. Got: " + new_frequency);
    return -1;
  }

  *frequency = new_frequency;
  timer.changePeriodFromISR(*frequency);
  timer.resetFromISR();

  log.appendFromISR("Set frequency to " + String(*frequency));
  return 0;
}

int PeriodicSubsystem::cloudCommand(String command) {
  log.debugFromISR("PeriodicSubsystem::cloudCommand\t" + command);
  errno = 0;
  int new_frequency = strtol(command.c_str(), NULL, 10);
  if (errno != 0) {
    log.appendFromISR("Error updating frequency. Got: " + command);
    return -1;
  }
  return setFrequencyFromISR(new_frequency);
}
