#include <Particle.h>

#include "Subsystem.h"

void Subsystem::setup() {
}

LoopStatus Subsystem::loop() {
  return FinishedError;
}

String Subsystem::getResult() {
  return "ERR";
}
