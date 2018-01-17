//***********************************
//* SD LOG HELPERS
//***********************************

#include <Particle.h>

#include "FileLog.h"

void FileLog::append(String str) {
  this->sd.Write(this->filename, str);
}
