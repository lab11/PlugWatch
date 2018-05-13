#include <Particle.h>

#include "Light.h"

LoopStatus loop() {
  *lux = light_sensor.getLux();
  return FinishedSuccess;
}

String Light::getReading() {
  String message = String(*lux);
  //log.append("Light! Lux: " + message);
  return(message);
}
