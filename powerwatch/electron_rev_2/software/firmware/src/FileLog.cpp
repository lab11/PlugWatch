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
  Serial.println(this->filename + ": " + str);
  this->sd.Write(this->filename, str);
}
