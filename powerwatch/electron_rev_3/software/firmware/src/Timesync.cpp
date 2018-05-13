#include "Timesync.h"

void Timesync::sync() {
  if (! Particle.syncTimePending()) { // if not currently syncing
    unsigned long now = millis();
    unsigned long last = Particle.timeSyncedLast();

    if ((now - last) > Timesync::TWELVE_HOURS) { // been a while
      Particle.syncTime(); // kick off a sync
    }
  }
}

LoopStatus Timesync::loop() {
    sync();
    return FinishedSuccess;
}
