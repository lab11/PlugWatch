#include <Particle.h>
#include "AssetTracker.h"

#include "Gps.h"
#include "FileLog.h"

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
  String LatLon = "";
  String GPSmilli = "";
  String GPSsatellites = "";
  if (t.gpsFix()) {
    LatLon = t.readLatLon();
  } else {
    LatLon = "-1";
  }

  if(t.getSatellites() > 0) {
    GPSmilli = String(t.getGpsTimestamp());
  } else {
    GPSmilli = "-1";
  }

  GPSsatellites = String(t.getSatellites());
  result = LatLon + String(MINOR_DLIM) + GPSmilli + String(MINOR_DLIM) + GPSsatellites;
  return FinishedSuccess;
}

String Gps::getResult() {
  return result;
}
