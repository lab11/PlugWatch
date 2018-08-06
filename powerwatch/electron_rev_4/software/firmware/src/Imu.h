#pragma once

#include <Particle.h>

#include "lis2dw12.h"
#include "Subsystem.h"

#define IMU_INT C4

class Imu: public Subsystem {
  typedef Subsystem super;

  String self_test_str;

  String result;

public:
  lis2dw12 accel;
  void setup();
  LoopStatus loop();
  String getResult();
};
