#pragma once

#include <Particle.h>

#include <MPU9250.h>

#include "Subsystem.h"

class Imu: public Subsystem {
  typedef Subsystem super;

  MPU9250 myIMU;
  String self_test_str;

  int* sample_count;
  int* sample_rate;
  Timer sample_timer;
  bool sampling = false;
  bool sample_flag = false;
  String sample_buffer;
  int current_count;

public:
  Imu(int* sample_count, int* sample_rate) :
    Subsystem(),
    sample_count { sample_count },
    sample_rate { sample_rate },
    sample_timer { Timer(*sample_rate, &Imu::sampleTimerCallback, *this) } {}

  void setup();
  LoopStatus loop();

  String self_test();

private:
  void sampleTimerCallback();
  void start_sampling();
  String do_sample();
};
