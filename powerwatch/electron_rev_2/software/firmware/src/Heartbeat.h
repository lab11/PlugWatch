#pragma once

#include <Particle.h>

#include "FileLog.h"
#include "SDCard.h"
#include "Subsystem.h"

class Heartbeat: public PeriodicSubsystem {
  typedef PeriodicSubsystem super;

  bool force_flag = false;
  int* count;

public:
  Heartbeat(SDCard &sd, int* frequency, int* count) :
    PeriodicSubsystem(sd, "heartbeat_log", frequency),
    count { count } {}

  static const int DEFAULT_FREQ = 1000 * 60 * 15;  // 15 min

  // Called every main loop
  void loop();

private:
  void periodic();
  void send() { send(false); }
  void send(bool force);
  String cloudFunctionName() { return "hb"; }

  // "enable", "disable", "get frequency"/"gf" "get count"/"gc", "now", or send
  // a numeric frequency (auto-enables)
  int cloudCommand(String command);
};
