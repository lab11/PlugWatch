#include <Particle.h>

#include "Light.h"

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
