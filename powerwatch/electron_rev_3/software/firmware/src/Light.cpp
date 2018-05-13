#include <Particle.h>

#include "Light.h"

LoopStatus Light::loop() {
  *lux = light_sensor.getLux();
  return FinishedSuccess;
}

String Light::getResult() {
  result = String(*lux);
  return(result);
}
