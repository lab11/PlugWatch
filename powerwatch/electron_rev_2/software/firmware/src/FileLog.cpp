//***********************************
//* SD LOG HELPERS
//***********************************

#include <Particle.h>

#include "FileLog.h"

void FileLog::appendFromISR(String str) {
  Serial.println(filename + ": " + str);
  // TODO: Enqueue and destack
}

void FileLog::append(String str) {
  Serial.println(filename + ": " + str);
  //sd.Write(filename, str);
}

void FileLog::debugFromISR(String str) {
  Serial.println("DEBUG\t" + filename + ": " + str);
}

void FileLog::debug(String str) {
  Serial.println("DEBUG\t" + filename + ": " + str);
}
