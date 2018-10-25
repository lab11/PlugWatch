#pragma once

#include <Particle.h>

#include "FileLog.h"
#include "SDCard.h"
#include "Subsystem.h"

#include "ISL29035.h"

class Audio: public PeriodicSubsystem {
  typedef PeriodicSubsystem super;

  ISL29035 audio_sensor;
  float* hz_and_mag;

public:
  static const int DEFAULT_FREQ = 1000 * 60 * 1;  // 1 min

  Audio(SDCard &sd, int* frequency, float* hz_and_mag) :
    PeriodicSubsystem(sd, "audio_log", frequency),
    hz_and_mag { hz_and_mag }
    { audio_sensor.init(); }

private:
  void periodic(bool force);
  void send(bool force);
  String cloudFunctionName() { return "ad"; }

  //   - "get hz and mag"/"ga"   Return audio value
  virtual int cloudCommand(String command);
};