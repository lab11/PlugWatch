//***********************************
//* SD LOG HELPERS
//***********************************

#include <Particle.h>

#include "Cloud.h"
#include "FileLog.h"

void FileLog::appendFromISR(String str) {
  Serial.println(filename + ": " + str);
  // TODO: Enqueue and destack
}

void FileLog::append(String str) {
  Serial.println(filename + ": " + str);
  //sd.Write(filename, str);
}

void FileLog::errorFromISR(String str) {
  Serial.println("ERROR|" + filename + ": " + str);
}

void FileLog::error(String str) {
  Serial.println("ERROR|" + filename + ": " + str);
  Cloud::Publish(ERROR_EVENT, str);
}

void FileLog::debugFromISR(String str) {
  Serial.println("DEBUG|" + filename + ": " + str);
}

void FileLog::debug(String str) {
  Serial.println("DEBUG|" + filename + ": " + str);
}
