#pragma once

#include <Particle.h>

#include "lis2dw12.h"
#include "Subsystem.h"

#define IMU_INT C4

class Imu: public Subsystem {
  typedef Subsystem super;

  String self_test_str;

  uint8_t motion_threshold;

  String result;

public:
  static const uint8_t DEFAULT_MOTION_THRESHOLD = 0x10;

  Imu(uint8_t motion_threshold) :
    Subsystem(),
    motion_threshold { motion_threshold } {}
   
  lis2dw12 accel;
  void setup();
  LoopStatus loop();
  String getResult();
};
