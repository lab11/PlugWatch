#include "Serial5/Serial5.h"

String response;
const String endstring = "OK\r\n";
char lastthree[3];
bool done = true;

void setup() {
  Serial.begin();
  // Reset and set baud rate to 9600 if at 115200
  Serial5.begin(115200);
  Serial5.println("AT+RST");
  delay(1000);
  Serial5.println("AT+UART_CUR=9600,8,1,0,0");
  delay(1000);

  // Set baud to 9600 and set mode to client
  Serial5.end();
  Serial5.begin(9600);
  Serial5.println("AT");
  Serial5.println("AT+CWMODE=1");
  delay(1000);
  //response = "";
  //done = false;
}

void serialEvent5() {
  if(!done) {
    while (Serial5.available()) {
      response.concat(Serial5.readString());
    }
  }
}

void loop() {
  if (response.endsWith(endstring)) {
    done = true;
  }
  if (done) {
    Serial.println();
    Serial.print(response);
    done = false;
    response = "";
    delay(5000);
    Serial5.println("AT+CWLAP");
  }
}
