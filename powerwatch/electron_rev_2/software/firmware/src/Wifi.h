#pragma once

#include <Particle.h>

#include "FileLog.h"
#include "SDCard.h"
#include "Subsystem.h"

#include "ESP8266.h"

#include <set>


class Wifi: public PeriodicSubsystem {
  typedef PeriodicSubsystem super;

  const int WIFI_POWER_EN = B0;
  ESP8266 esp8266;
  String* response;
  bool* done;
  std::set<String> ssid_set;


public:
  static const int DEFAULT_FREQ = 1000 * 60 * 1;  // 1 min

  Wifi(SDCard &sd, ESP8266 &esp8266, int* frequency, String* response, bool* done):
  PeriodicSubsystem(sd, "wifi_log", frequency),
  esp8266 { esp8266 },
  response { response },
  done { done } {}

private:
  void periodic(bool force);
  void send(bool force);
  void construct_ssid_list();
  String cloudFunctionName() { return "wf"; }

  virtual int cloudCommand(String command);
};
