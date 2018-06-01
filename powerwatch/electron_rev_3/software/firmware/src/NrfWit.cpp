#include <Particle.h>
#include <Serial4/Serial4.h> // C3(TX) and C2(RX)

#include "NrfWit.h"
#include "Base64RK.h"

void NrfWit::setup() {
  super::setup();

  reset();

  Serial4.begin(115200);
  Serial4.setTimeout(100);

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

      int start = result.indexOf("!M");
      int end = result.indexOf("\n", start);
      if(start >= 0 && end >= 0 && result.length() > start + 63) {
        //We actually need to parse this otu of the conection
        int voltage = 0;
        int current = 0;
        int wattage = 0;
        int power = 0;
        int frequency = 0;

        // Voltage
        int vstart = result.indexOf("V",start) + 1;
        int vend = result.indexOf("C",start);
        if(vstart >=0 && vend >=0) {
            voltage = result.substring(vstart,vend).toInt();
        }

        // current
        int cstart = result.indexOf("C",start) + 1;
        int cend = result.indexOf("W",start);
        if(cstart >=0 && cend >=0) {
            current = result.substring(cstart,cend).toInt();
        }

        // wattage
        int wstart = result.indexOf("W",start) + 1;
        int wend = result.indexOf("P",start);
        if(wstart >=0 && wend >=0) {
            wattage = result.substring(wstart,wend).toInt();
        }

        // frequency
        int fstart = result.indexOf("F",start) + 1;
        int fend = result.indexOf("\n",start);
        if(fstart >=0 && fend >=0) {
            frequency = result.substring(fstart,fend).toInt();
        }

        uint32_t buf[4];
        buf[0] = voltage;
        buf[1] = current;
        buf[2] = wattage;
        buf[3] = frequency;

        Serial.printlnf("Volt: %d, Curr: %d, Watt: %d, Freq: %d", voltage,current,wattage,frequency);
        result = Base64::encodeToString((uint8_t*)buf, 16);

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
