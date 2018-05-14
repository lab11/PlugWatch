#include <Particle.h>

#include "Cloud.h"
#include "Wifi.h"
#include "FileLog.h"

void Wifi::construct_ssid_list() {
  size_t place = 0;
  Serial.println();
  while (place < serial_response->length() and place >= 0) {
    size_t first = serial_response->indexOf('"', place);
    size_t second = serial_response->indexOf('"', first+1);
    String ssid = serial_response->substring(first+1, second);
    ssid_set.insert(ssid);
    place = serial_response->indexOf("CWLAP", second);
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

enum WifiState {
  Idle,
  Scanning,
};

LoopStatus Wifi::loop() {
  static WifiState state;

  switch (state) {
    case Idle: {
      ssid_set.clear();
      scan_start_time = millis();
      esp8266.beginScan();

      state = Scanning;
      return NotFinished;
    }

    case Scanning: {
      if (! *serial_done) {
        return NotFinished;
      }

      *serial_done = false;
      construct_ssid_list();

      state = Idle;
      return FinishedSuccess;
    }
  }
}
