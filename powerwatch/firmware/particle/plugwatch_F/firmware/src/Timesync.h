#pragma once

#include <Particle.h>
#include "Subsystem.h"
#include "AB1815.h"

//***********************************
//* Time Sync
//*
//* Particle synchronizes its clock when it first connects. Over time, it will
//* drift away from real time. This routine will re-sync local time.
//***********************************

class Timesync: public Subsystem {
  typedef Subsystem super;

  const int TWELVE_HOURS = 1000 * 60 * 60 * 12;
  AB1815 rtc;

public:
  void setup();
  LoopStatus loop();

};
