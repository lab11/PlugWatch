#include <Particle.h>

#include "Cloud.h"
#include "Wifi.h"
#include "FileLog.h"

void Wifi::construct_ssid_list() {
  size_t place = 0;
  while (place >= 0) {
    size_t first = response->indexOf('"');
    size_t second = response->indexOf('"', first+1);
    String ssid = response->substring(first+1, second);
    //Serial.println(ssid);
  }
}

void Wifi::send(bool force) {
    String message = "WIFI";//String(*count)+String("|")+String(meta);
    if (force) {
      message = "FORCE|" + message;
    }
    log.append(message);
    Cloud::Publish(WIFI_SCAN_EVENT, message);
}

void Wifi::periodic(bool force) {
  Serial.println("Wifi periodic!");
  ssid_set.clear();
  esp8266.beginScan();
  unsigned long time = millis();
  // block until response;
  while (!done) {
    unsigned long now = millis();
    // time out if > 5 seconds waiting
    if (now - time > 5000) {
      Serial.println(now - time);
      break;
    }
  }
  if (done) {
    // construct list, send/log success
    construct_ssid_list();
    log.append("Wifi Scan! Count: " + String(ssid_set.size()));
    send(force);
  }
  else {
    // log/send failure
    log.append("Wifi Scan Error! Timeout");
    Cloud::Publish(WIFI_ERROR_EVENT, String("Timeout"));
  }
}

int Wifi::cloudCommand(String command) {
//  if ((command == "gc") || (command == "get count")) {
//    return *count;
//  }

  return super::cloudCommand(command);
}
