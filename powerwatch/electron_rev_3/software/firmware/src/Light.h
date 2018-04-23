#pragma once

#include <Particle.h>

#include "Subsystem2.h"
#include "FileLog.h"
#include "SDCard.h"
#include "Cloud.h"

#include "ISL29035.h"

class Light: public PeriodicSubsystem2 {
  typedef PeriodicSubsystem2 super;

  ISL29035 light_sensor;
  float* lux;

public:
  static const int DEFAULT_FREQ = 1000 * 60 * 60 * 2;  // 2 hr

  Light(SDCard &sd, int* frequency, float* lux) :
    PeriodicSubsystem2(sd, "light_log", frequency),
    lux { lux }
    {
      light_sensor.init();
      classTag = LIGHT_EVENT;
    }
    String getReading();

private:
  String cloudFunctionName() { return "lt"; }

  //   - "get lux"/"gl"   Return lux value
  virtual int cloudCommand(String command);
};
