#include <SdFat.h>
#include <Particle.h>

SYSTEM_MODE(MANUAL);



int led1 = B0;
int led2 = C0;

int chip_in = A0;
int val = 0;     // variable to store the read value

SdFatSoftSpi<A4, A5, A3> sd; //soft_miso, soft_mosi, soft_sck
const uint8_t chipSelect = A2;
File myFile;



void setup() {

  Serial.begin(9600);

  pinMode(led1, OUTPUT);
  pinMode(led2, OUTPUT);
  pinMode(chip_in, INPUT);
  //pinMode(enable, OUTPUT);


  if (!sd.begin(chipSelect, SPI_HALF_SPEED)) {
    Serial.println("initErrorHalt");
    sd.initErrorHalt();
  }
   if (!myFile.open("test2.txt", O_RDWR | O_CREAT | O_AT_END)) {
     Serial.println("openErrorHalt");
    sd.errorHalt("opening test.txt for write failed");
  }
  // if the file opened okay, write to it:
  Serial.print("Writing to test.txt...");
  myFile.println("testing 1, 2, 3.");
  myFile.printf("fileSize: %d\n", myFile.fileSize());

  // close the file:
  myFile.close();
  Serial.println("done.");


}


void loop() {
  val = analogRead(chip_in);
  Serial.println(val);
  digitalWrite(led1, val);
}
