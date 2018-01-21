#pragma once

#include <Particle.h>

#include "FileLog.h"
#include "SDCard.h"

class Subsystem {

protected:
  FileLog log;

  virtual String cloudFunctionName() = 0;
  virtual int cloudCommand(String command) = 0;

public:
  explicit Subsystem(SDCard &sd, String logName) :
    log { FileLog(sd, logName) } {}

  // Call during initial setup
  virtual void setup();

  // Called every main loop
  virtual void loop();
};


class PeriodicSubsystem: public Subsystem {
  typedef Subsystem super;

protected:
  virtual int cloudCommand(String command);

  const int MIN_FREQ = 1000 * 1;
  const int MAX_FREQ = 1000 * 60 * 60;

  int* frequency; // should point to retained memory for this instance
  Timer timer;
  bool timer_flag = false;

  virtual void periodic() = 0;
  virtual void timerCallback();
  int setFrequencyFromISR(int frequency);

public:
  PeriodicSubsystem(SDCard &sd, String logName, int* frequency) :
    Subsystem(sd, logName),
    frequency { frequency },
    timer { Timer(*frequency, &PeriodicSubsystem::timerCallback, *this) } {}

  virtual void setup();
  virtual void loop();
};
