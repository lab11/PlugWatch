#include <Particle.h>
#include <Serial4/Serial4.h> // C3(TX) and C2(RX)

#include "NrfWit.h"

void NrfWit::setup() {
  super::setup();

  reset();

  Serial4.begin(115200);
  Serial4.setTimeout(500);

  // Clear out the current buffer
  while(Serial4.available()) {
    Serial4.read();
  }
}

void NrfWit::reset() {
  // Reset the NRF
  pinMode(D7, OUTPUT);
  digitalWrite(D7, LOW);
  delay(1000);
  digitalWrite(D7, HIGH);
  delay(500);

  // Set it back to input in case the button gets pressed
  pinMode(D7, INPUT);
}

LoopStatus NrfWit::loop() {
  if (Serial4.available()) {
    String msg = Serial4.readString();

    int start = msg.indexOf("\r");
    if (start == -1) return FinishedSuccess;

    int end = msg.indexOf("\n", start);
    if (end == -1) return FinishedSuccess;

    if(msg.length() > 62) {
      result = msg.substring(start+45, start+63);
    } else {
      return FinishedSuccess;
    }


    return FinishedSuccess;
  } else {
    // It really is an error if serial is unavailable
    return FinishedError;
  }

}

String NrfWit::getResult() {
  // Clear advertisement on read
  String temp = result;
  result = "";
  return temp;
}
