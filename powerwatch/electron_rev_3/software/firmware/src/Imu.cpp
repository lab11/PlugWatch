#include <Particle.h>

// https://build.particle.io/libs/SparkFun_MPU-9250/1.0.0/tab/example/MPU9250BasicAHRS.ino
#include <MPU9250.h>
#include <quaternionFilters.h>

#include "Imu.h"
#include "FileLog.h"

void Imu::setup() {
  super::setup();

  // TODO: Report error if this fails and don't try to use IMU in this session
  // self_test_str = self_test();

  //set the motion threshold interrupt as an input
  pinMode(IMU_INT, INPUT);

  // Setup the wake on motion interrupt
  setWakeOnMotion();

  // Clear the interrupt by reading the interrupt status register
  Serial.printlnf("%X",myIMU.readByte(MPU9250_ADDRESS, INT_STATUS));
}

LoopStatus Imu::loop() {
  super::loop();

  // Read the temperature sensor
  int temp = myIMU.readTempData();
  Serial.printlnf("temp data: %d",temp);

  // Convert to temperature
  float tempf = ((float)temp)/333.87 + 21.0;
  char temp_str[5];
  snprintf(temp_str, 5, "%0.2f", tempf);

  // Sample the wake on Interrupt pin
  if(digitalRead(IMU_INT)) {
    result = "1" + String(MINOR_DLIM) + String(temp_str);

    // Clear the interrupt
    myIMU.readByte(MPU9250_ADDRESS, INT_STATUS);
  } else {
    result = "0" + String(MINOR_DLIM) + String(temp_str);
  }

  return FinishedSuccess;
}

void Imu::setWakeOnMotion() {

  //Reset Device
  myIMU.writeByte(MPU9250_ADDRESS, PWR_MGMT_1, 0x80);
  delay(1000);

  myIMU.writeByte(MPU9250_ADDRESS, PWR_MGMT_1, 0x00);

  //PWR_MGMT_2 set DIS_XA, DIS_YA, DIS_ZA = 0, DIS_XG, DIS_YG, DIS_ZG = 1
  myIMU.writeByte(MPU9250_ADDRESS, PWR_MGMT_2, 0x07);

  //****
  //* Set Acell LPF setting to 184hz BandWidth
  //****
  //ACCEL_CONFIG_@ set ACCEL_FCHOICE_B = 1 and A_DLPFCFG[2:] = 1(b001)
  myIMU.writeByte(MPU9250_ADDRESS, ACCEL_CONFIG_2, 0x01);

  //****
  //* Set the motion interrupt to latch mode
  //****
  myIMU.writeByte(MPU9250_ADDRESS, INT_PIN_CFG, 0x20);

  //****
  //* Enable Motion Interrupt
  //****
  //INT_ENABLE set whole register to 0x40 to enable motion interrupt only
  myIMU.writeByte(MPU9250_ADDRESS, INT_ENABLE, 0x40);

  //****
  //* Enable Accel Hardware Intelligence
  //****
  //MOT_DETECT_CTRL set ACCEL_INTEL_EN=1 and ACCEL_INTEL_MODE=1
  myIMU.writeByte(MPU9250_ADDRESS, MOT_DETECT_CTRL, 0xC0);

  //****
  //* Set Motion Threshold
  //****
  //WOM_THR set WOM_THRESH[7:0] to 1~255 LSBs (0!1020mg)
  myIMU.writeByte(MPU9250_ADDRESS, WOM_THR, 0x1F); //TODO think about this value

  //****
  //* Set Wakeup Frequency
  //****
  //LP_ACCEL_ODR set LPOSC_CLKSEL [3:0] to 0.24Hz ~ 500Hz
  myIMU.writeByte(MPU9250_ADDRESS, LP_ACCEL_ODR, 0x06);

  //****
  //* Enable Cycle Mode
  //****
  //PWR_MGMT_1 make cycle=1
  myIMU.writeByte(MPU9250_ADDRESS, PWR_MGMT_1, 0x20);
}

String Imu::self_test() {
   byte c = myIMU.readByte(MPU9250_ADDRESS, WHO_AM_I_MPU9250);
   String myIMU_st = "";
   String ak_st = "";
   myIMU_st += String(c,HEX);
   myIMU_st += MINOR_DLIM;
   if (c == 0x71) {
     myIMU.MPU9250SelfTest(myIMU.SelfTest);
     myIMU_st += String(myIMU.SelfTest[0]);
     myIMU_st += MINOR_DLIM;
     myIMU_st += String(myIMU.SelfTest[1]);
     myIMU_st += MINOR_DLIM;
     myIMU_st += String(myIMU.SelfTest[2]);
     myIMU_st += MINOR_DLIM;
     myIMU_st += String(myIMU.SelfTest[3]);
     myIMU_st += MINOR_DLIM;
     myIMU_st += String(myIMU.SelfTest[4]);
     myIMU_st += MINOR_DLIM;
     myIMU_st += String(myIMU.SelfTest[5]);

     myIMU.calibrateMPU9250(myIMU.gyroBias, myIMU.accelBias);
     myIMU.initMPU9250();
     byte d = myIMU.readByte(AK8963_ADDRESS, WHO_AM_I_AK8963);
     myIMU.initAK8963(myIMU.magCalibration);
     ak_st += String(d,HEX);
     ak_st += MINOR_DLIM;
     ak_st += String(myIMU.magCalibration[0]);
     ak_st += MINOR_DLIM;
     ak_st += String(myIMU.magCalibration[1]);
     ak_st += MINOR_DLIM;
     ak_st += String(myIMU.magCalibration[2]);
   } // if (c == 0x71)
   else {
     //log.error("Could not connect to MPU9250: 0x" + String(c, HEX));
   }
   return myIMU_st + "\n" + ak_st;
 }

String Imu::do_sample() {
  // If intPin goes high, all data registers have new data
  // On interrupt, check if data ready interrupt
  if (myIMU.readByte(MPU9250_ADDRESS, INT_STATUS) & 0x01) {
    myIMU.readAccelData(myIMU.accelCount);  // Read the x/y/z adc values
    myIMU.getAres();

    // Now we'll calculate the accleration value into actual g's
    myIMU.ax = (float)myIMU.accelCount[0]*myIMU.aRes; // - accelBias[0];
    myIMU.ay = (float)myIMU.accelCount[1]*myIMU.aRes; // - accelBias[1];
    myIMU.az = (float)myIMU.accelCount[2]*myIMU.aRes; // - accelBias[2];

    myIMU.readGyroData(myIMU.gyroCount);  // Read the x/y/z adc values
    myIMU.getGres();

    // Calculate the gyro value into actual degrees per second
    myIMU.gx = (float)myIMU.gyroCount[0]*myIMU.gRes;
    myIMU.gy = (float)myIMU.gyroCount[1]*myIMU.gRes;
    myIMU.gz = (float)myIMU.gyroCount[2]*myIMU.gRes;

    myIMU.readMagData(myIMU.magCount);  // Read the x/y/z adc values
    myIMU.getMres();
    myIMU.magbias[0] = +470.;
    myIMU.magbias[1] = +120.;
    myIMU.magbias[2] = +125.;

    // Calculate the magnetometer values in milliGauss
    // Include factory calibration per data sheet and user environmental
    // corrections
    // Get actual magnetometer value, this depends on scale being set
    myIMU.mx = (float)myIMU.magCount[0]*myIMU.mRes*myIMU.magCalibration[0] -
    myIMU.magbias[0];
    myIMU.my = (float)myIMU.magCount[1]*myIMU.mRes*myIMU.magCalibration[1] -
    myIMU.magbias[1];
    myIMU.mz = (float)myIMU.magCount[2]*myIMU.mRes*myIMU.magCalibration[2] -
    myIMU.magbias[2];
  } // if (readByte(MPU9250_ADDRESS, INT_STATUS) & 0x01)

  myIMU.updateTime();
  MahonyQuaternionUpdate(myIMU.ax, myIMU.ay, myIMU.az, myIMU.gx*DEG_TO_RAD,
                         myIMU.gy*DEG_TO_RAD, myIMU.gz*DEG_TO_RAD, myIMU.my,
                         myIMU.mx, myIMU.mz, myIMU.deltat);

  myIMU.delt_t = millis() - myIMU.count;
  String time_str = String(Time.format(Time.now(), TIME_FORMAT_ISO8601_FULL));
  String myIMU_st = time_str;
  myIMU_st += MINOR_DLIM;
  if (myIMU.delt_t > 1) { //GO AS FAST AS POSSIBLE
    String x_accel_mg = String(1000*myIMU.ax);
    myIMU_st += x_accel_mg;
    String y_accel_mg = String(1000*myIMU.ay);
    myIMU_st += MINOR_DLIM;
    myIMU_st += y_accel_mg;
    String z_accel_mg = String(1000*myIMU.az);
    myIMU_st += MINOR_DLIM;
    myIMU_st += z_accel_mg;
    String x_gyro_ds = String(myIMU.gx);
    myIMU_st += MINOR_DLIM;
    myIMU_st += x_gyro_ds;
    String y_gyro_ds = String(myIMU.gy);
    myIMU_st += MINOR_DLIM;
    myIMU_st += y_gyro_ds;
    String z_gyro_ds = String(myIMU.gz);
    myIMU_st += MINOR_DLIM;
    myIMU_st += z_gyro_ds;
    String x_mag_mg = String(myIMU.mx);
    myIMU_st += MINOR_DLIM;
    myIMU_st += x_mag_mg;
    String y_mag_mg = String(myIMU.my);
    myIMU_st += MINOR_DLIM;
    myIMU_st += y_mag_mg;
    String z_mag_mg = String(myIMU.mz);
    myIMU_st += MINOR_DLIM;
    myIMU_st += z_mag_mg;
    myIMU.tempCount = myIMU.readTempData();  // Read the adc values
    myIMU.temperature = ((float) myIMU.tempCount) / 333.87 + 21.0; // Temperature in degrees Centigrade
    String tmp_c = String(myIMU.temperature);
    myIMU_st += MINOR_DLIM;
    myIMU_st += tmp_c;
    myIMU.count = millis();
  }
  return myIMU_st;
}

String Imu::getResult() {
    return result;
}
