#pragma once

#include <Particle.h>

#include "FileLog.h"
#include "SDCard.h"
#include "Subsystem.h"

class Heartbeat: public PeriodicSubsystem {
  typedef PeriodicSubsystem super;

  int* count;

public:
  static const int DEFAULT_FREQ = 1000 * 60 * 15;  // 15 min

  Heartbeat(SDCard &sd, int* frequency, int* count) :
    PeriodicSubsystem(sd, "heartbeat_log", frequency),
    count { count } {}

private:
  void periodic(bool force);
  void send(bool force);
  String cloudFunctionName() { return "hb"; }

  //   - "get count"/"gc"   Return heartbeat count
  virtual int cloudCommand(String command);
};
