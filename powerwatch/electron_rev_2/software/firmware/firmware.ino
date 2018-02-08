#include "Serial5/Serial5.h"

String response;
String data;
const String endstring = "OK\r\n";
char lastthree[3];
bool done;

void setup() {
  Serial.begin();
  Serial5.begin(115200);
  Serial5.println("AT+RST");
  delay(1000);
  Serial5.println("AT+CWMODE=1");
  delay(1000);
  while(Serial5.available()) {
    Serial5.read();
  }
  Serial5.println("AT+CWLAP");
}

void serialEvent5() {
  while (Serial5.available()) {
    data = Serial5.readString();
    response.concat(data);
    if (data.endsWith(endstring)) {
      done = true;
      break;
    }
  }
}

void loop() {
  if (done) {
    Serial.println();
    Serial.print(response);
    delay(5000);
    Serial5.println("AT+CWLAP");
    done = false;
    response = "";
  }
}
