#include <Particle.h>

#include "Cloud.h"
#include "Audio.h"
#include "FileLog.h"

void Audio::send(bool force) {
  String message = String(*hz_and_mag);
  if (force) {
    message = "FORCE|" + message;
  }
  log.append(message);
  Cloud::Publish(AUDIO_EVENT, message);
}

void Audio::periodic(bool force) {
  //TODO
  /*
  *hz_and_mag = audio_sensor.getHzMag();
  log.append("Audio! Hz: " + String(*hz_and_mag));
  send(force);
  */
}

int Audio::cloudCommand(String command) {
  if ((command == "ga") || (command == "get audio")) {
    return *hz_and_mag;
  }

  return super::cloudCommand(command);
}
