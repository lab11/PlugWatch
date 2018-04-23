#include <Particle.h>

#include "Cloud.h"

retained int cloud_publish_cnt = 0; //TODO

void Cloud::setup() {
   Particle.variable("a", cloud_publish_cnt);
}

void Cloud::Publish(String tag, String message) {
  bool success;
  String time_str = String(Time.format(Time.now(), TIME_FORMAT_ISO8601_FULL));
  String to_publish = time_str + String(DLIM) + message;
  success = Particle.publish(tag, to_publish);
  //TODO write to publish log
  cloud_publish_cnt = cloud_publish_cnt + 1;
}
