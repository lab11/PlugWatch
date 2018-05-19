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
  static bool first = true;
  static unsigned long mill = 0;
  if(first) {
    mill = millis();
    first = false;
  }

  if(millis() - mill < 15000) {
    if (Serial4.available()) {
      String msg = Serial4.readString();
      result.concat(msg);

      int start = result.indexOf("\r");
      int end = result.indexOf("\n", start);
      if(start >= 0 && end >= 0 && result.length() > start + 63) {
        result = msg.substring(start+45, start+63);
        first = true;
        return FinishedSuccess;
      }

      return NotFinished;
    } else {
      return NotFinished;
    }
  } else {
    first = true;
    return FinishedError;
  }


}

String NrfWit::getResult() {
  // Clear advertisement on read
  String temp = result;
  result = "";
  return temp;
}
