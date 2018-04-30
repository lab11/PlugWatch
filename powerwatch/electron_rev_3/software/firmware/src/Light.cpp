#include <Particle.h>

#include "Light.h"
#include "FileLog.h"

void Light::send(bool force) {
  String message = String(*lux);
  if (force) {
    message = "F|" + message;
  }
  log.append(message);
  Cloud::Publish(LIGHT_EVENT, message);
}

void Light::periodic(bool force) {
  *lux = light_sensor.getLux();
  log.append("Lux: " + String(*lux));
  send(force);
}

// add a cloud command to return the current value of the light
// (without doing a publish):
int Light::cloudCommand(String command) {
  if ((command == "gl") || (command == "get light")) {
    return *lux;
  }

  return super::cloudCommand(command);
}

String Light::getReading() {
  log.debug("Light::getReading() called");
  *lux = light_sensor.getLux();
  String message = String(*lux);
  log.append("Light! Lux: " + message);
  return(message);
}
