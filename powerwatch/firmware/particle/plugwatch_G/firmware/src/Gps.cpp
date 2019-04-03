#include <Particle.h>
#include "AssetTrackerRK.h"

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
  static bool first = true;
  static long start = 0;
  if(first) {
    start = millis();
    first = false;
  }

  if(millis() - start < 10000) {
    t.updateGPS();
    return NotFinished;
  } else {
    String LatLon = "";
    String GPStime = "";
    String GPSsatellites = "";
    if (t.gpsFix()) {
      LatLon = t.readLatLon();
    } else {
      LatLon = "-1";
    }

    if(t.getTinyGPSPlus()->satellites.value() > 0) {
      struct tm current_time;
      current_time.tm_year = t.getTinyGPSPlus()->date.year() - 1900;
      current_time.tm_mon = t.getTinyGPSPlus()->date.month() - 1;
      current_time.tm_mday = t.getTinyGPSPlus()->date.day();
      current_time.tm_hour = t.getTinyGPSPlus()->time.hour();
      current_time.tm_min = t.getTinyGPSPlus()->time.minute();
      current_time.tm_sec = t.getTinyGPSPlus()->time.second();
      current_time.tm_isdst = 0;

      //convert it into a time_t object
      unsigned long utime = ((unsigned long)mktime(&current_time));
      GPStime = String(utime);
    } else {
      GPStime = "-1";
    }

    GPSsatellites = String(t.getTinyGPSPlus()->satellites.value());
    result = LatLon + String(MINOR_DLIM) + GPStime + String(MINOR_DLIM) + GPSsatellites;
    Serial.printlnf("GPS: %s",result.c_str());
    first = true;
    return FinishedSuccess;
  }
}

String Gps::getResult() {
  return result;
}
