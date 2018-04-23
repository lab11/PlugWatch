#include <Particle.h>

#include "Subsystem2.h"

void PeriodicSubsystem2::loop() {
  super::loop();

  if (timer_flag) {
    log.append("PeriodicSubsystem2 Timer event.");
    run(rPeriodic);
    timer_flag = false;
  }
}

void PeriodicSubsystem2::setup() {
  super::setup();
  timer.start();
  log.append("Periodic setup complete. Initial frequency " + String(*frequency));
}

void PeriodicSubsystem2::timerCallback() {
  log.debug("PeriodicSubsystem2::timerCallback() firing; timer_flag set to true");
  timer_flag = true;
}

void PeriodicSubsystem2::run(runReasonCode reason) {
  log.debug("PeriodicSubsystem2::run called");
  String message = "";
  message = this->getReading();
  String messagePrefix = "";
  switch (reason) {
    case rPeriodic:
      messagePrefix = "P";
      break;
    case rCloud:
      messagePrefix = "C";
      break;
    case rOther:
      messagePrefix = "O";
      break;
    case rChargeState:
      messagePrefix = "S";
      break;
    default:
      // just leave blank for now
      break;
  }
  message = messagePrefix + DLIM + message;

  // really this needs to be broken up into separate log
  // and Publish events, especially since there may be data or
  // Publish message caps.
  log.append(message);
  Cloud::Publish(eventTag, message);
}

int PeriodicSubsystem2::setFrequencyFromISR(int new_frequency) {
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

// XXXX take a long look at these; not sure where these timer startFromISR
// and stopFromISR calls are defined, and if we want "now" or "force" to
// actually
int PeriodicSubsystem2::cloudCommand(String command) {
  log.debugFromISR("PeriodicSubsystem2::cloudCommand\t" + command);

  if (command == "enable") {
    timer.startFromISR();
    return 0;
  }
  if (command == "disable") {
    timer.stopFromISR();
    return 0;
  }
  if ((command == "now") || (command == "force") || (command == "get")) {
    run(rCloud);
    return 0;
  }
  if ((command == "gf") || (command == "get frequency")) {
    return *frequency;
  }

  // assume command is a number and set timer frequency to that number
  // This might not be the best to just blindly set all numbers, eh..
  errno = 0;
  int new_frequency = strtol(command.c_str(), NULL, 10);
  if (errno != 0) {
    log.appendFromISR("Error updating frequency. Got: " + command);
    return -1;
  }
  return setFrequencyFromISR(new_frequency);
}
