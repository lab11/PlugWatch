#include <Particle.h>

#include "Cloud.h"

bool Cloud::Publish(String tag, String message) {
  bool success;
  String time_str = String(Time.format(Time.now(), TIME_FORMAT_ISO8601_FULL));
  String to_publish = time_str + String(MAJOR_DLIM) + message;
  success = Particle.publish(tag, to_publish, PRIVATE);
  return success;
}
