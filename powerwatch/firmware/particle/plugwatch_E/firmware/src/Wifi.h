#pragma once

#include <set>

#include <Particle.h>

#include "Subsystem.h"

#include "ESP8266.h"


class Wifi: public Subsystem {
  typedef Subsystem super;

  ESP8266 esp8266;
  std::set<uint8_t> ssid_set;
  bool force;

public:
  Wifi(ESP8266 &esp8266):
  esp8266 { esp8266 } {}

  LoopStatus loop();
  String getResult();

private:
  void periodic(bool force);
  void send(bool force);
  void construct_ssid_list();
};
