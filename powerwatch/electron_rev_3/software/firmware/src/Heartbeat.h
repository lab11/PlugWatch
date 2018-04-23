#pragma once

#include <Particle.h>

#include "Cloud.h"
#include "FileLog.h"
#include "SDCard.h"
#include "Subsystem2.h"

class Heartbeat: public PeriodicSubsystem2 {
  typedef PeriodicSubsystem2 super;

  int* count;

public:
  static const int DEFAULT_FREQ = 1000 * 60 * 15;  // 15 min

  Heartbeat(SDCard &sd, int* frequency, int* count) :
    PeriodicSubsystem2(sd, "heartbeat_log", frequency),
    count { count } { eventTag = HEARTBEAT_EVENT; *count = 0; }
    String getReading();

private:
  String cloudFunctionName() { return "hb"; }

  //   - "get count"/"gc"   Return heartbeat count
  virtual int cloudCommand(String command);
};
