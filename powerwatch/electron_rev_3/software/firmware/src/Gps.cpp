#include <Particle.h>
#include "AssetTracker.h"

#include "Gps.h"
#include "Subsystem.h"
#include "Cloud.h"

AssetTracker t = AssetTracker();

void Gps::setup() {
  super::setup();

  // GPS setup here
  t.begin();
  t.gpsOn();
}

void Gps::periodic(bool force) {
  // GPS periodic operation here
  t.updateGPS();

}

int Gps::cloudCommand(String command) {
  if ((command == "gps") || (command == "get gps")) {
      send(true);
  }
  return super::cloudCommand(command);
}

void Gps::send(bool force) {
  String message = "-1";
  if (t.gpsFix()) {
      message = t.readLatLon();
  }
  if (force) {
    message = "F|" + message;
  }
  log.append(message);
  Cloud::Publish(GPS_EVENT, message);
}
