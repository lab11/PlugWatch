#pragma once

#include <Particle.h>

class ESP8266 {
  protected:
    String *response;
    bool *done;

  public:
    ESP8266(String* response, bool* done);
    void beginScan();
    void loop();
};
