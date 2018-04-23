#pragma once

#include <Particle.h>
#include <Subsystem.h>
#include "Cloud.h"


class PeriodicSubsystem2: public Subsystem {
  typedef Subsystem super;

protected:
  // Subclasses may override `frequency` bounds
  const int MIN_FREQ = 1000 * 1;
  const int MAX_FREQ = 1000 * 60 * 60 * 24;
  virtual int minFreq() { return MIN_FREQ; }
  virtual int maxFreq() { return MAX_FREQ; }

  // Subclasses must define:
  String eventTag = "";

  // why was run() called?
  enum runReasonCode {
    rPeriodic,
    rCloud,
    rChargeState,
    rOther,
  };
  // do a reading (and for now, also publish and log it):
  virtual void run(runReasonCode reason);

  // Provides commands:
  //   - "enable",              Enable periodic operation
  //   - "disable",             Disable periodic operation
  //   - "get frequency"/"gf"   Return current frequency
  //   - "now"/"force"          Call run immediately
  //   - any valid number       Set frequency and enable periodic operation
  //
  // Subclasses should add their own methods, then call super if none match.
  virtual int cloudCommand(String command);
  // run calls this to get the data and format the send and log data


private:
  int* frequency; // should point to retained memory for this instance
  Timer timer;

  void timerCallback();
  int setFrequencyFromISR(int frequency);

public:
  PeriodicSubsystem2(SDCard &sd, String logName, int* frequency) :
    Subsystem(sd, logName),
    frequency { frequency },
    timer { Timer(*frequency, &PeriodicSubsystem2::timerCallback, *this) } {}

  bool timer_flag = false;
  virtual void setup();
  virtual void loop();
  virtual String getReading() = 0;



};
