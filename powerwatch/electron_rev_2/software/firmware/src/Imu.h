#pragma once

#include <Particle.h>

#include "FileLog.h"
#include "SDCard.h"
#include "Subsystem.h"

class Imu: public PeriodicSubsystem {
  typedef PeriodicSubsystem super;

  String self_test_str;

  int* sample_count;
  int* sample_rate;
  Timer sample_timer;
  bool sampling = false;
  bool sample_flag = false;
  String sample_buffer;
  int current_count;

public:
  static const int DEFAULT_FREQ = 1000 * 60;  // 1 min
  static const int DEFAULT_SAMPLE_COUNT = 512;
  static const int DEFAULT_SAMPLE_RATE_MS = 4; // 250 Hz

  Imu(SDCard &sd, int* frequency, int* sample_count, int* sample_rate) :
    PeriodicSubsystem(sd, "imu_log", frequency),
    sample_count { sample_count },
    sample_rate { sample_rate },
    sample_timer { Timer(*sample_rate, &Imu::sampleTimerCallback, *this) } {}

  void setup();
  void loop();

  String self_test();

private:
  void periodic();
  String cloudFunctionName() { return "imu"; }

  void sampleTimerCallback();
  void start_sampling();
  String do_sample();
};
