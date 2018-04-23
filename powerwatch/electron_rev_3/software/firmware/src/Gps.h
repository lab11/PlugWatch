#pragma once

#include <Particle.h>

#include "Subsystem2.h"
#include "Cloud.h"

class Gps: public PeriodicSubsystem2 {
  typedef PeriodicSubsystem2 super;

public:
//  static const int DEFAULT_FREQ = 1000 * 60 * 10;  // 10 min
  static const int DEFAULT_FREQ = 1000 * 60;  // XXXX 1m FOR DEBUGGING:

  Gps(SDCard &sd, int* frequency) :
  PeriodicSubsystem2(sd, "gps_log", frequency) {
    eventTag = GPS_EVENT;
  }

  // Arduino setup
  void setup();
  String getReading();

private:
//  virtual int cloudCommand(String command);
  String cloudFunctionName() { return "gps"; }
};
