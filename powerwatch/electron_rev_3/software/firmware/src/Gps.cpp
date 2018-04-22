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

String Gps::getReading() {
  // XXXX do we need to run the updateGPS every loop() instead?
  // ...it used to be called in periodic()
  log.debug("Gps::getReading() called");
  t.updateGPS();
  String message = "-1";
  if (t.gpsFix()) {
      message = t.readLatLon();
  }
  log.debug("Gps::getReading() done and message set to: " + message);
  return(message);
}
