#include <Particle.h>
#include "AssetTracker.h"

#include "Gps.h"

AssetTracker t = AssetTracker();

void Gps::setup() {
  super::setup();

  // GPS-specifc setup here
  t.begin();
  t.gpsOn();
}

LoopStatus Gps::loop() {
  //log.debug("Gps::getReading() called");
  t.updateGPS();
  String message = "-1";
  if (t.gpsFix()) {
      message = t.readLatLon();
  }
  //log.debug("Gps::getReading() done and message set to: " + message);
  this->result = message;

  return FinishedSuccess;
}

String Gps::getResult() {
  return this->result;
}
