#pragma once

#include <Particle.h>

#include "FileLog.h"
#include "SDCard.h"

class Heartbeat {
  FileLog log;
  Timer timer;

  bool timer_flag = false;
  bool force_flag = false;

public:
  const int MIN_FREQ = 1000 * 5;
  const int MAX_FREQ = 1000 * 60 * 60;

  Heartbeat(SDCard &sd) :
    log { FileLog(sd, "heartbeat_log.txt") },
    timer { Timer(frequency, &Heartbeat::timerCallback, *this) } {}

  // Call during initial setup
  void setup();

  // Called every main loop
  void loop();

private:
  void sendHeartbeat(bool force);
  void timerCallback();
  int setFrequencyFromISR(int frequency);

  // "enable", "disable", "get frequency"/"gf" "get count"/"gc", "now", or send
  // a numeric frequency (auto-enables)
  int cloudCommand(String command);

  static int frequency;
  static int count;
};
