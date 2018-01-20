#pragma once

#include <Particle.h>

#include "FileLog.h"
#include "SDCard.h"

class ChargeState {
  FileLog log;
  Timer timer;
  String message;

  bool timer_flag = false;

public:
  static const int DEFAULT_FREQ = 1000 * 60 * 15;  // 15 min
  static const int MIN_FREQ = 1000 * 5;            // 5 sec
  static const int MAX_FREQ = 1000 * 60 * 60;      // 1 hour

  const String CHARGE_STATE_BATTERY = "b";
  const String CHARGE_STATE_WALL = "w";

  ChargeState(SDCard &sd) :
    log { FileLog(sd, "charge_state_log.txt") },
    timer { Timer(frequency, &ChargeState::timerCallback, *this) } {}

  // Call during initial setup
  void setup();

  // Called every main loop
  void loop();

private:
  void send();
  void timerCallback();
  int setFrequencyFromISR(int frequency);

  // "enable", "disable", "get frequency"/"gf" "get count"/"gc", "now", or send
  // a numeric frequency (auto-enables)
  int cloudCommand(String command);

  static int frequency;
};
