#include <Particle.h>

// https://build.particle.io/libs/SparkFun_MPU-9250/1.0.0/tab/example/MPU9250BasicAHRS.ino
#include "lis2dw12.h"
#include "Imu.h"
#include "FileLog.h"

void Imu::setup() {
  //set the motion threshold interrupt as an input
  pinMode(IMU_INT, INPUT);
    
  // This code along with the driver was taken from the permamote
  // repo and slightly modified to fit our use case (I2C, latched interrupt)
  accel.config_for_wake_on_motion(100);

  // Clear the interrupt by reading the interrupt status register
  delay(1000);
  accel.read_status();
}

LoopStatus Imu::loop() {
  super::loop();

  // Sample the wake on Interrupt pin
  if(digitalRead(IMU_INT)) {
    result = "1" + String(MINOR_DLIM) + String(0);

    // Clear the interrupt
    accel.read_status();
  } else {
    result = "0" + String(MINOR_DLIM) + String(0);
  }

  return FinishedSuccess;
}

String Imu::getResult() {
    return result;
}
