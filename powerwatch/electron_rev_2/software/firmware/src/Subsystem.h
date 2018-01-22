#pragma once

#include <Particle.h>

#include "FileLog.h"

class SDCard;
class Subsystem {

protected:
  FileLog log;

  virtual String cloudFunctionName() = 0;
  virtual int cloudCommand(String command) = 0;

public:
  explicit Subsystem(SDCard &sd, String logName) :
    log { FileLog(sd, logName) } {}

  // Called during initial setup. Subclasses must call super first.
  virtual void setup();

  // Called every main loop. Subclasses must call super first.
  virtual void loop();
};


class PeriodicSubsystem: public Subsystem {
  typedef Subsystem super;

protected:
  // Called from `loop` context every `frequency` milliseconds.
  virtual void periodic(bool force) = 0;

  // Subclasses may override `frequency` bounds
  const int MIN_FREQ = 1000 * 1;
  const int MAX_FREQ = 1000 * 60 * 60;
  virtual int minFreq() { return MIN_FREQ; }
  virtual int maxFreq() { return MAX_FREQ; }

  // Provides commands:
  //   - "enable",              Enable periodic operation
  //   - "disable",             Disable periodic operation
  //   - "get frequency"/"gf"   Return current frequency
  //   - "now"/"force"          Call `periodic` immediately (force=true)
  //   - any valid number       Set frequency and enable periodic operation
  //
  // Subclasses should add their own methods, then call super if none match.
  virtual int cloudCommand(String command);

private:
  int* frequency; // should point to retained memory for this instance
  Timer timer;
  bool timer_flag = false;
  bool force_flag = false;

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
