#include <Particle.h>

#include "Cloud.h"
#include "Wifi.h"
#include "FileLog.h"

void Wifi::construct_ssid_list() {
  size_t place = 0;
  Serial.println();
  while (place < response->length() and place >= 0) {
    size_t first = response->indexOf('"', place);
    size_t second = response->indexOf('"', first+1);
    String ssid = response->substring(first+1, second);
    ssid_set.insert(ssid);
    place = response->indexOf("CWLAP", second);
  }
}

String Wifi::getResult() {
    String message = String(ssid_set.size());
    for (auto i = ssid_set.begin(); i != ssid_set.end(); ++i) {
      message += DLIM;
      message += *i;
    }
    //log.append(message);
    return message;
}

void Wifi::periodic(bool force) {
  force = force;
  ssid_set.clear();
  //log.append("WIFI| Began scan!");
  scan_start_time = millis();
  esp8266.beginScan();
}

LoopStatus Wifi::loop() {
  // TODO: integrate starting and such

  if (*done) {
    *done = false;
    // construct list, send/log success
    construct_ssid_list();
    //log.append("Wifi Scan! Count: " + String(ssid_set.size()));

    return FinishedSuccess;
  } else {
    return NotFinished;
  }
}
