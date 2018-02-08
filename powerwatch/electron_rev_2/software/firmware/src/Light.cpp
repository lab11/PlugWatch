#include <Particle.h>

#include "Cloud.h"
#include "Light.h"
#include "FileLog.h"

void Light::send(bool force) {
  String message = String(*lux);
  if (force) {
    message = "FORCE|" + message;
  }
  log.append(message);
  Cloud::Publish(LIGHT_EVENT, message);
}

void Light::periodic(bool force) {
  *lux = light_sensor.getLux();
  log.append("Light! Lux: " + String(*lux));
  send(force);
}

int Light::cloudCommand(String command) {
  if ((command == "gl") || (command == "get light")) {
    return *lux;
  }

  return super::cloudCommand(command);
}
