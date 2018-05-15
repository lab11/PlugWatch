#include <Particle.h>
#include <Serial4/Serial4.h> // C3(TX) and C2(RX)

#include "NrfWit.h"

void NrfWit::setup() {
  super::setup();

  Serial4.begin(115200);
}

LoopStatus NrfWit::loop() {
  if (Serial4.available()) {
    String msg = Serial4.readString();

    int start = msg.indexOf("\r");
    if (start == -1) return FinishedSuccess;

    int end = msg.indexOf("\n", start);
    if (end == -1) return FinishedSuccess;

    result = msg.substring(start, end);
  }

  return FinishedSuccess;
}

String NrfWit::getResult() {
  // Clear advertisement on read
  String temp = result;
  result = "";
  return temp;
}
