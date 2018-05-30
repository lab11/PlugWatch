#include <Particle.h>

#include "Cloud.h"
#include "OneWire.h"
#include "Wifi.h"
#include "FileLog.h"
#include "Base64RK.h"

void Wifi::construct_ssid_list() {
  size_t place = 0;
  Serial.println();
  String serial_response = esp8266.getResult();
  while (place < serial_response.length() and place >= 0) {
    size_t first = serial_response.indexOf('"', place);
    size_t second = serial_response.indexOf('"', first+1);
    String ssid = serial_response.substring(first+1, second);

    // Take the crc16 of the ssid - this is a makeshift hash
    uint16_t crc = OneWire::crc16((uint8_t*)ssid.c_str(),ssid.length());

    //put the hash in our set
    ssid_set.insert(crc & 0xff);
    place = serial_response.indexOf("CWLAP", second);
  }
}

String Wifi::getResult() {
    uint8_t buf[100];
    buf[0] = ssid_set.size();

    uint8_t j = 1;
    for (auto i = ssid_set.begin(); i != ssid_set.end(); ++i) {
      buf[j]= *i;
      j++;
    }

    //log.append(message);
    return Base64::encodeToString(buf, j + 1);
}

enum WifiState {
  Idle,
  Scanning,
};

LoopStatus Wifi::loop() {
  static WifiState state = Idle;

  switch (state) {
    case Idle: {
      ssid_set.clear();
      esp8266.powerOn();
      esp8266.beginScan();

      state = Scanning;
      return NotFinished;
    }

    case Scanning: {
      LoopStatus r = esp8266.loop();

      if(r == FinishedSuccess) {
        construct_ssid_list();
        state = Idle;
        esp8266.powerOff();
        return FinishedSuccess;
      } else if (r == NotFinished) {
        return NotFinished;
      } else {
        state = Idle;
        esp8266.powerOff();
        return FinishedError;
      }
    }
  }
}
