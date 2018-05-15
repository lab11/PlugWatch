#include <Particle.h>

// https://build.particle.io/libs/SparkFun_MPU-9250/1.0.0/tab/example/MPU9250BasicAHRS.ino
#include <MPU9250.h>
#include <quaternionFilters.h>

#include "Imu.h"
#include "FileLog.h"

void Imu::setup() {
  super::setup();

  // TODO: Report error if this fails and don't try to use IMU in this session
  self_test_str = self_test();
}

LoopStatus Imu::loop() {
  super::loop();

  static unsigned current_count = 0;
  static unsigned long last_sample_time;

  // Idea: Take 1s worth of IMU data at 10Hz (10 samples)
  if (current_count < 10) {
    // Sample rate enforcement:
    if (current_count == 0) {
      result = "";
      last_sample_time = millis();
    } else if ((millis() - last_sample_time) < 100) {
      return NotFinished;
    } else {
      last_sample_time = millis();
    }

    // TODO: I'm not sure that do_sample actually works, need HW
    //String sample_result = do_sample();
    String sample_result = "IMU Sample";
    result += MINOR_DLIM + String(sample_result);

    current_count++;
    return NotFinished;
  } else {
    current_count = 0;
    return FinishedSuccess;
  }
}

void Imu:setWakeOnMotion() {

  //Reset Device
  myIMU.writeByte(MPU9250_ADDRESS, PWR_MGMT_1, 0x80);
  delay(100);

  //Set to default clock sources
  myIMU.writeByte(MPU9250_ADDRESS, PWR_MGMT_1, 0x01);
  myIMU.writeByte(MPU9250_ADDRESS, PWR_MGMT_2, 0x00);
  delay(200);

  //****
  //* Make sure accel is running
  //****
  //PWR_MGMT_1 make cycle=0, sleep=0, standby=0
  cur_reg = imu.readByte(MPU9250_ADDRESS, PWR_MGMT_1);
  data = cur_reg &= ~0x70;
  myIMU.writeByte(MPU9250_ADDRESS, PWR_MGMT_1, data);

  //PWR_MGMT_2 set DIS_XA, DIS_YA, DIS_ZA = 0, DIS_XG, DIS_YG, DIS_ZG = 1
  myIMU.writeByte(MPU9250_ADDRESS, PWR_MGMT_2, 0x07);

  //****
  //* Set Acell LPF setting to 184hz BandWidth
  //****
  //ACCEL_CONFIG_@ set ACCEL_FCHOICE_B = 1 and A_DLPFCFG[2:] = 1(b001)
  myIMU.writeByte(MPU9250_ADDRESS, ACCEL_CONFIG_2, 0x05);

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
  myIMU.writeByte(MPU9250_ADDRESS, LP_ACCEL_ODR, 0x03);

  //****
  //* Enable Cycle Mode
  //****
  //PWR_MGMT_1 make cycle=1
  cur_reg = imu.readByte(MPU9250_ADDRESS, PWR_MGMT_1);
  data = cur_reg |= 0x20;
  myIMU.writeByte(MPU9250_ADDRESS, PWR_MGMT_1, data);
}

String Imu::self_test() {
   byte c = myIMU.readByte(MPU9250_ADDRESS, WHO_AM_I_MPU9250);
   String imu_st = "";
   String ak_st = "";
   imu_st += String(c,HEX);
   imu_st += MINOR_DLIM;
   if (c == 0x71) {
     myIMU.MPU9250SelfTest(myIMU.SelfTest);
     imu_st += String(myIMU.SelfTest[0]);
     imu_st += MINOR_DLIM;
     imu_st += String(myIMU.SelfTest[1]);
     imu_st += MINOR_DLIM;
     imu_st += String(myIMU.SelfTest[2]);
     imu_st += MINOR_DLIM;
     imu_st += String(myIMU.SelfTest[3]);
     imu_st += MINOR_DLIM;
     imu_st += String(myIMU.SelfTest[4]);
     imu_st += MINOR_DLIM;
     imu_st += String(myIMU.SelfTest[5]);

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
   return imu_st + "\n" + ak_st;
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
  String imu = time_str;
  imu += MINOR_DLIM;
  if (myIMU.delt_t > 1) { //GO AS FAST AS POSSIBLE
    String x_accel_mg = String(1000*myIMU.ax);
    imu += x_accel_mg;
    String y_accel_mg = String(1000*myIMU.ay);
    imu += MINOR_DLIM;
    imu += y_accel_mg;
    String z_accel_mg = String(1000*myIMU.az);
    imu += MINOR_DLIM;
    imu += z_accel_mg;
    String x_gyro_ds = String(myIMU.gx);
    imu += MINOR_DLIM;
    imu += x_gyro_ds;
    String y_gyro_ds = String(myIMU.gy);
    imu += MINOR_DLIM;
    imu += y_gyro_ds;
    String z_gyro_ds = String(myIMU.gz);
    imu += MINOR_DLIM;
    imu += z_gyro_ds;
    String x_mag_mg = String(myIMU.mx);
    imu += MINOR_DLIM;
    imu += x_mag_mg;
    String y_mag_mg = String(myIMU.my);
    imu += MINOR_DLIM;
    imu += y_mag_mg;
    String z_mag_mg = String(myIMU.mz);
    imu += MINOR_DLIM;
    imu += z_mag_mg;
    myIMU.tempCount = myIMU.readTempData();  // Read the adc values
    myIMU.temperature = ((float) myIMU.tempCount) / 333.87 + 21.0; // Temperature in degrees Centigrade
    String tmp_c = String(myIMU.temperature);
    imu += MINOR_DLIM;
    imu += tmp_c;
    myIMU.count = millis();
  }
  return imu;
}

String Imu::getResult() {
    return result;
}
