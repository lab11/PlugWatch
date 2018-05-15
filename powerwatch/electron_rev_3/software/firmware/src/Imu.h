#pragma once

#include <Particle.h>

#include <MPU9250.h>

#include "Subsystem.h"

#define MPU9250_ADDRESS 0x69  // Device address when ADO = 1
#define PWR_MGMT_1 0x6B
#define PWR_MGMT_2 0x6C
#define ACCEL_CONFIG_2 0x1D
#define INT_ENABLE 0x38
#define MOT_DETECT_CTRL 0x69
#define WOM_THR 0x1F
#define LP_ACCEL_ODR 0x1E

class Imu: public Subsystem {
  typedef Subsystem super;

  MPU9250 myIMU;
  String self_test_str;

  int* motion_threshold;

  String result;

public:
  static const int DEFAULT_MOTION_THRESHOLD = 0.1; //0.1g motion threshold

  Imu(int* motion_threshold) :
    Subsystem(),
    motion_threshold { motion_threshold } {}

  void setup();
  LoopStatus loop();
  String getResult();

  String self_test();

private:
  uint8_t cur_reg;
  uint8_t data;
  void sampleTimerCallback();
  String do_sample();
  void setWakeOnMotion();
};
