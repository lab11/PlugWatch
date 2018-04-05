#include <Particle.h>
#include <Serial4/Serial4.h> // C3(TX) and C2(RX)

#include "Cloud.h"
#include "FileLog.h"
#include "NrfWit.h"

void NrfWit::setup() {
  super::setup();

  Serial4.begin(115200);
}

void NrfWit::loop() {
  String msg = "";
  msg = Serial4.readString();
  log.append("NrfWit Got: " + msg);
}

int NrfWit::cloudCommand(String command) {
  if ((command == "gw") || (command == "get wit")) {
    // TODO
    return -1;
  }

  //return super::cloudCommand(command);
  return -2;
}
