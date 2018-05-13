#pragma once

#include <Particle.h>

#include "Subsystem.h"
#include "FileLog.h"

class CellStatus: public Subsystem {
  typedef Subsystem super;

  String result;
public:
  LoopStatus loop();
  String getResult();
};
