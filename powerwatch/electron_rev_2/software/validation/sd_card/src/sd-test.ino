#include <SdFat.h>
#include <Particle.h>

SYSTEM_MODE(MANUAL);



int led1 = B0;
int led2 = C0;

int chip_in = A0;
int val = 0;     // variable to store the read value

SdFatSoftSpi<A5, A4, A3> sd;
const uint8_t chipSelect = A2;
File myFile;



void setup() {

  Serial.begin(9600);

  pinMode(led1, OUTPUT);
  pinMode(led2, OUTPUT);
  pinMode(chip_in, INPUT);
  //pinMode(enable, OUTPUT);


  while (!Serial) {
    SysCall::yield();
  }

  Serial.println("Type any character to start");
  while (Serial.read() <= 0) {
    SysCall::yield();
  }
  if (!sd.begin(chipSelect, SPI_HALF_SPEED)) {
    sd.initErrorHalt();
  }
   if (!myFile.open("test2.txt", O_RDWR | O_CREAT | O_AT_END)) {
    sd.errorHalt("opening test.txt for write failed");
  }
  // if the file opened okay, write to it:
  Serial.print("Writing to test.txt...");
  myFile.println("testing 1, 2, 3.");
  myFile.printf("fileSize: %d\n", myFile.fileSize());

  // close the file:
  myFile.close();
  Serial.println("done.");

  // re-open the file for reading:
  if (!myFile.open("test.txt", O_READ)) {
    sd.errorHalt("opening test.txt for read failed");
  }
  Serial.println("test.txt content:");

  // read from the file until there's nothing else in it:
  int data;
  while ((data = myFile.read()) >= 0) {
    Serial.write(data);
  }
  // close the file:
  myFile.close();

}


void loop() {
  val = analogRead(chip_in);
  Serial.println(val);
  digitalWrite(led1, val);
}
