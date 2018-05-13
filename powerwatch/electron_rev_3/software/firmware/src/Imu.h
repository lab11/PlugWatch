#pragma once

#include <Particle.h>

#include <MPU9250.h>

#include "Subsystem.h"

class Imu: public Subsystem {
  typedef Subsystem super;

  MPU9250 myIMU;
  String self_test_str;
  
  int* motion_threshold;
  bool sampling = false;
  bool sample_flag = false;
  String sample_buffer;
  int current_count;

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
  void sampleTimerCallback();
  void start_sampling();
  String do_sample();
};
