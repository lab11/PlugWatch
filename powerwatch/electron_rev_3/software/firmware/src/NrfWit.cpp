#include <Particle.h>
#include <Serial4/Serial4.h> // C3(TX) and C2(RX)

#include "NrfWit.h"

void NrfWit::setup() {
  super::setup();

  Serial4.begin(115200);
}

LoopStatus NrfWit::loop() {
  String msg = "";
  msg = Serial4.readString();
  //log.append("NrfWit Got: " + msg);

  this->result = msg;
  return FinishedSuccess;
}

String NrfWit::getResult() {
  return this->result;
}
