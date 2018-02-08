#pragma once

#include <Particle.h>

#include "FileLog.h"
#include "SDCard.h"
#include "Subsystem.h"

#include "ISL29035.h"

class Light: public PeriodicSubsystem {
  typedef PeriodicSubsystem super;

  ISL29035 light_sensor;
  float* lux;

public:
  static const int DEFAULT_FREQ = 1000 * 60 * 1;  // 1 min

  Light(SDCard &sd, int* frequency, float* lux) :
    PeriodicSubsystem(sd, "light_log", frequency),
    lux { lux }
    { light_sensor.init(); }

private:
  void periodic(bool force);
  void send(bool force);
  String cloudFunctionName() { return "lt"; }

  //   - "get lux"/"gl"   Return lux value
  virtual int cloudCommand(String command);
};
