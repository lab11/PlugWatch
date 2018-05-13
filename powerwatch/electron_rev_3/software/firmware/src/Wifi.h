#pragma once

#include <set>

#include <Particle.h>

#include "Subsystem.h"

#include "ESP8266.h"


class Wifi: public Subsystem {
  typedef Subsystem super;

  ESP8266 esp8266;
  String* response;
  bool* done;
  std::set<String> ssid_set;
  unsigned long scan_start_time;
  bool force;

public:
  Wifi(ESP8266 &esp8266, String* response, bool* done):
  esp8266 { esp8266 },
  response { response },
  done { done } {}

  LoopStatus loop();
  String getResult();

private:
  void periodic(bool force);
  void send(bool force);
  void construct_ssid_list();
};
