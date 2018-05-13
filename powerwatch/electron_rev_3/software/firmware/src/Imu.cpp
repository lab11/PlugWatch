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

void Imu::start_sampling() {
  if (!sampling) {
    sampling = true;
    current_count = 0;
    sample_timer.start();
  }
}

LoopStatus Imu::loop() {
  super::loop();

  if (sample_flag) {
    sample_flag = false;

    if (current_count >= *sample_count) {
      sample_timer.stop();

      Serial.println("ending sample");
      Serial.println(sample_buffer);
      //log.append(sample_buffer);
      sample_buffer = "";
    } else {
      current_count += 1;
      String res = do_sample();
      sample_buffer = String(sample_buffer) + String(res) + String("\n");
    }
  }
}

void Imu::sampleTimerCallback() {
  sample_flag = true;
}

String Imu::self_test() {
   byte c = myIMU.readByte(MPU9250_ADDRESS, WHO_AM_I_MPU9250);
   String imu_st = "";
   String ak_st = "";
   imu_st += String(c,HEX);
   imu_st += DLIM;
   if (c == 0x71) {
     myIMU.MPU9250SelfTest(myIMU.SelfTest);
     imu_st += String(myIMU.SelfTest[0]);
     imu_st += DLIM;
     imu_st += String(myIMU.SelfTest[1]);
     imu_st += DLIM;
     imu_st += String(myIMU.SelfTest[2]);
     imu_st += DLIM;
     imu_st += String(myIMU.SelfTest[3]);
     imu_st += DLIM;
     imu_st += String(myIMU.SelfTest[4]);
     imu_st += DLIM;
     imu_st += String(myIMU.SelfTest[5]);

     myIMU.calibrateMPU9250(myIMU.gyroBias, myIMU.accelBias);
     myIMU.initMPU9250();
     byte d = myIMU.readByte(AK8963_ADDRESS, WHO_AM_I_AK8963);
     myIMU.initAK8963(myIMU.magCalibration);
     ak_st += String(d,HEX);
     ak_st += DLIM;
     ak_st += String(myIMU.magCalibration[0]);
     ak_st += DLIM;
     ak_st += String(myIMU.magCalibration[1]);
     ak_st += DLIM;
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
  imu += DLIM;
  if (myIMU.delt_t > 1) { //GO AS FAST AS POSSIBLE
    String x_accel_mg = String(1000*myIMU.ax);
    imu += x_accel_mg;
    String y_accel_mg = String(1000*myIMU.ay);
    imu += DLIM;
    imu += y_accel_mg;
    String z_accel_mg = String(1000*myIMU.az);
    imu += DLIM;
    imu += z_accel_mg;
    String x_gyro_ds = String(myIMU.gx);
    imu += DLIM;
    imu += x_gyro_ds;
    String y_gyro_ds = String(myIMU.gy);
    imu += DLIM;
    imu += y_gyro_ds;
    String z_gyro_ds = String(myIMU.gz);
    imu += DLIM;
    imu += z_gyro_ds;
    String x_mag_mg = String(myIMU.mx);
    imu += DLIM;
    imu += x_mag_mg;
    String y_mag_mg = String(myIMU.my);
    imu += DLIM;
    imu += y_mag_mg;
    String z_mag_mg = String(myIMU.mz);
    imu += DLIM;
    imu += z_mag_mg;
    myIMU.tempCount = myIMU.readTempData();  // Read the adc values
    myIMU.temperature = ((float) myIMU.tempCount) / 333.87 + 21.0; // Temperature in degrees Centigrade
    String tmp_c = String(myIMU.temperature);
    imu += DLIM;
    imu += tmp_c;
    myIMU.count = millis();
  }
  return imu;
}

String Imu::getResult() {
    return result;
}
