#pragma once

#include <Particle.h>

#include "FileLog.h"
#include "SDCard.h"
#include "Subsystem.h"

class NrfWit: public Subsystem {
  typedef Subsystem super;

public:
  static const int DEFAULT_FREQ = 1000 * 60 * 1;  // 1 min

  NrfWit(SDCard &sd) :
    Subsystem(sd, "nrfwit_log") { }

  void setup();
  void loop();

private:
  String cloudFunctionName() { return "lt"; }

  //   - "get wit"/"gw"   Return latest wit
  virtual int cloudCommand(String command);
};
