#include "Timesync.h"

LoopStatus Timesync::loop() {
  if(Particle.connected()) {
    if (Particle.syncTimePending()) {
      return NotFinished;
    } else {
      // Do we need to sync?
      unsigned long now = millis();
      unsigned long last = Particle.timeSyncedLast();

      if ((now - last) > Timesync::TWELVE_HOURS) { // been a while
        Particle.syncTime(); // kick off a sync
        return NotFinished;
      } else {
        return FinishedSuccess;
      }
    }
  } else {
    return FinishedError;
  }
}
