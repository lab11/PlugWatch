#pragma once

#include <Particle.h>

#include "Subsystem.h"

class ESP8266: public Subsystem {
  String response;
  unsigned long start_time;

public:
  ESP8266();
  void beginScan();
  LoopStatus loop();
  String getResult();
};
