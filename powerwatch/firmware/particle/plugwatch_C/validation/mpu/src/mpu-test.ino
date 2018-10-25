#include "quaternionFilters.h"
#include "MPU9250.h"
#include <SPI.h>

#define AHRS false         // Set to false for basic data read
#define SerialDebug true  // Set to true to get Serial output for debugging


int intPin = D2;  // These can be changed, 2 and 3 are the Arduinos ext int pins
int myLed  = B0;  // Set up pin 13 led for toggling
SYSTEM_MODE(MANUAL);    // prevent load of modem occurring automatically

MPU9250 myIMU;
String imu_self_test_str;

String self_test_imu() {
  byte c = myIMU.readByte(MPU9250_ADDRESS, WHO_AM_I_MPU9250);
  String imu_st = "";
  String ak_st = "";
  imu_st += String(c,HEX);
  imu_st += ",";
  if (c == 0x71) {
    myIMU.MPU9250SelfTest(myIMU.SelfTest);
    imu_st += String(myIMU.SelfTest[0]);
    imu_st += ",";
    imu_st += String(myIMU.SelfTest[1]);
    imu_st += ",";
    imu_st += String(myIMU.SelfTest[2]);
    imu_st += ",";
    imu_st += String(myIMU.SelfTest[3]);
    imu_st += ",";
    imu_st += String(myIMU.SelfTest[4]);
    imu_st += ",";
    imu_st += String(myIMU.SelfTest[5]);

    myIMU.calibrateMPU9250(myIMU.gyroBias, myIMU.accelBias);
    myIMU.initMPU9250();
    byte d = myIMU.readByte(AK8963_ADDRESS, WHO_AM_I_AK8963);
    myIMU.initAK8963(myIMU.magCalibration);
    ak_st += String(d,HEX);
    ak_st += ",";
    ak_st += String(myIMU.magCalibration[0]);
    ak_st += ",";
    ak_st += String(myIMU.magCalibration[1]);
    ak_st += ",";
    ak_st += String(myIMU.magCalibration[2]);
  } // if (c == 0x71)
  else {
    Serial.print("Could not connect to MPU9250: 0x");
    Serial.println(c, HEX);
    //TODO send an error message out
  }
  return imu_st + "\n" + ak_st;
}

void setup()
{
  Wire.begin();
  Serial.begin(9600);
  pinMode(intPin, OUTPUT);
  pinMode(myLed, OUTPUT);
  imu_self_test_str = self_test_imu();
}

void loop() {
   //String imu_loop_str = imu_loop();
   Serial.println(imu_self_test_str);
   delay(1000);
}
