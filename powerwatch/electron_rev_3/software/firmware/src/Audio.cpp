#include <Particle.h>

#include "Audio.h"

String Audio::getReading() {
  //TODO
  /*
  *hz_and_mag = audio_sensor.getHzMag();
  log.append("Audio! Hz: " + String(*hz_and_mag));
  send(force);
  */
  return(String(*hz_and_mag));
}

int Audio::cloudCommand(String command) {
  if ((command == "ga") || (command == "get audio")) {
    return *hz_and_mag;
  }

  return super::cloudCommand(command);
}
