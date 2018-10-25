#include <Particle.h>

#include "Light.h"

void Light::setup() {
  light_sensor.init();
}

LoopStatus Light::loop() {
  lux = light_sensor.getLux();
  return FinishedSuccess;
}

String Light::getResult() {
  // This truncates the lux value, but decimal lux are almost certainly in the noise anyway.
  result = String((int) lux);
  return(result);
}
