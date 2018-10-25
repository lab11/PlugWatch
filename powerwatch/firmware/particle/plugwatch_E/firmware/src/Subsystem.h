#pragma once

#include <Particle.h>

enum LoopStatus {
  FinishedSuccess,
  FinishedError,
  NotFinished,
};

class Subsystem {
public:
  virtual void setup();

  virtual LoopStatus loop();

  virtual String getResult();
};
