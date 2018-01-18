/*
 * Project sensor_board_v2
 * Description: For Q2 2018 Deployment in Ghana
 * Author: Noah Klugman; Pat Pannuto
 */

#include <CellularHelper.h>
#include <google-maps-device-locator.h>
#include <Particle.h>
#include <MPU9250.h>
#include <quaternionFilters.h>

#include "Cloud.h"
#include "FileLog.h"
#include "Heartbeat.h"
#include "PowerCheck.h"
#include "SDCard.h"


//***********************************
//* TODO's
//***********************************
//WD
//Charge state interrupts
//System state logging
//Ack routine
//IMU readings on charge state
//SD interrupts

//***********************************
//* Critical System Config
//***********************************
PRODUCT_ID(4861);
PRODUCT_VERSION(6);
STARTUP(System.enableFeature(FEATURE_RESET_INFO));
STARTUP(System.enableFeature(FEATURE_RETAINED_MEMORY));
//ArduinoOutStream cout(Serial);
STARTUP(cellular_credentials_set("http://mtnplay.com.gh", "", "", NULL));
SYSTEM_MODE(MANUAL);

//**********************************
//* Pin Configuration
//**********************************
int debug_led_1 = C0;
int debug_led_2 = B0;


//***********************************
//* Watchdogs
//***********************************
const int HARDWARE_WATCHDOG_TIMEOUT_MS = 1000 * 60;
ApplicationWatchdog wd(HARDWARE_WATCHDOG_TIMEOUT_MS, System.reset);


//***********************************
//* SD Card & Logging
//***********************************
SDCard SD;
auto ChargeStateLog = FileLog(SD, "charge_state_log.txt");
auto ErrorLog = FileLog(SD, "error_log.txt");
auto EventLog = FileLog(SD, "event_log.txt");
auto FunctionLog = FileLog(SD, "function_log.txt");
auto SampleLog = FileLog(SD, "sample_log.txt");
auto SubscriptionLog = FileLog(SD, "subscription_log.txt");

//GoogleMapsDeviceLocator locator;


//***********************************
//* Heartbeat
//***********************************
auto heartbeat = Heartbeat(SD);

//***********************************
//* Charge state
//***********************************
const int CHARGE_STATE_MIN_FREQ = 10;
const int CHARGE_STATE_MAX_FREQ = 1000 * 60 * 60;
retained int charge_state_poll_frequency = 100;
Timer charge_state_poll_timer(charge_state_poll_frequency, charge_state_timer_callback);

PowerCheck powerCheck;
bool CHARGE_STATE_FLAG = false;
String CHARGE_STATE_MSG = "";
const String CHARGE_STATE_BATTERY = "b";
const String CHARGE_STATE_WALL = "w";

void charge_state_timer_callback() {
  static bool last_charge_state = false;
  bool charge_state = powerCheck.getIsCharging();

  if (charge_state == true) {
    digitalWrite(debug_led_2, LOW);
    CHARGE_STATE_MSG = CHARGE_STATE_WALL;
  } else {
    digitalWrite(debug_led_2, HIGH);
    CHARGE_STATE_MSG = CHARGE_STATE_BATTERY;
  }

  if (charge_state != last_charge_state) {
    ChargeStateLog.append("Charge state change to " + String(charge_state));
    CHARGE_STATE_FLAG = true;
    last_charge_state = charge_state;
  }
}


//***********************************
//* Application State
//***********************************
void take_a_sample();

//Timer cloud variables
retained int sample_frequency = 10;
retained int sample_buff_size = 1000;
#define ONE_DAY_MILLIS (24 * 60 * 60 * 1000)

//Timers
//Timer sample_poll_timer(sample_frequency, take_a_sample);


//Loop switches
bool RESET_FLAG = false;
bool SAMPLE_FLAG = false;
bool SD_READ_FLAG = false;

 String SD_LOG_NAME = "";
 //Soft timers
 unsigned long lastSync = millis();
 //***********************************
 //* Peripherals
 //***********************************
 MPU9250 myIMU; //TODO add a constant sample buffer... save last 1000 samples plus next 1000 samples on charge state change


 //***********************************
 //* Cloud Publish
 //***********************************

 //publish wrapper types
 String SYSTEM_EVENT = "s";
 String SUBSCRIPTION_EVENT = "r";
 String CHARGE_STATE_EVENT = "c";
 String TIME_SYNC_EVENT = "t";
 String ERROR_EVENT = "e";
 String CLOUD_FUNCTION_TEST_EVENT = "g";



 //***********************************
 //* Cloud Variables
 //***********************************
 // publish
 retained int cloud_publish_cnt = 0; //TODO
 retained int subscribe_cnt = 0; //TODO
 retained String last_publish_time = ""; //TODO

 // system events
 retained int system_event_cnt = 0;
 retained String last_system_event_time = "";
 retained int last_system_event_type = -999;
 retained int num_reboots = 0; //TODO

 // imu
 String imu_self_test_str;
 String imu_last_sample; //TODO

 String sample_buffer;
 retained int sample_cnt;

 //***********************************
 //* CLOUD FUNCTIONS
 //***********************************
int debug_sd(String file) {
  SD_READ_FLAG = true;
  SD_LOG_NAME = file;
  return 0;
}

void init_sample_buffer() {
    //sample_buffer = new int[sample_buff_size]
}

//TODO testing
int set_charge_state_poll_frequency(String frequency){ //cloudfunction
    charge_state_poll_frequency = frequency.toInt(); //TODO catch error
    charge_state_poll_timer.changePeriod(charge_state_poll_frequency);
    charge_state_poll_timer.reset();
    FunctionLog.append("set_charge_state_poll_frequency");
    return 0;
}

//TODO testing
int set_sample_poll_frequency(String frequency) { //cloudfunction
    sample_frequency = frequency.toInt(); //TODO catch error
    //sample_poll_timer.changePeriod(sample_frequency);
    //sample_poll_timer.reset();
    FunctionLog.append("set_sample_poll_frequency");
    //TODO restart timer
    return 0;
}

//TODO testing
int set_sample_buff(String frequency) { //cloudfunction
    sample_buff_size = frequency.toInt(); //TODO catch error
    init_sample_buffer();
    FunctionLog.append("set_sample_buff");
    return 0;
}

//TODO testing
int cloud_reboot(String ack) { //cloudfunction
  RESET_FLAG = true;
  FunctionLog.append("cloud_reboot");
  return ack.toInt();
}

int cloud_function_sd_power_cycle(String _unused_msg) {
  SD.PowerCycle();
  return 0;
}

 int get_soc(String c) { //cloudfunction
     return (int)(FuelGauge().getSoC());
 }

 int get_battv(String c) { //cloudfunction
     return (int)(100 * FuelGauge().getVCell());
 }

void start_sample();
int sample_test(String msg) //cloudfunction
{
  start_sample();
  return 1;
}

//***********************************
//* Sensors
//***********************************


//Subscription
void handle_particle_event(const char *event, const char *data)
{
  String msg = String(subscribe_cnt);
  msg = msg + String("|") + String(event) + String("|") + String(data);
  subscribe_cnt = subscribe_cnt + 1;
  String log_str = String(subscribe_cnt) + String("|");
  SubscriptionLog.append(log_str);
  publish_wrapper(SUBSCRIPTION_EVENT, msg);
}
//System Events
void handle_all_system_events(system_event_t event, int param) {
  system_event_cnt = system_event_cnt + 1;
  Serial.printlnf("got event %d with value %d", event, param);
  String system_event_str = String((int)event) + "|" + String(param);
  String time_str = String(Time.format(Time.now(), TIME_FORMAT_ISO8601_FULL));
  last_system_event_time = time_str;
  last_system_event_type = param;
  publish_wrapper(SYSTEM_EVENT, system_event_str);
  EventLog.append(system_event_str);
}
//IMU
String self_test_imu() {
   byte c = myIMU.readByte(MPU9250_ADDRESS, WHO_AM_I_MPU9250);
   String imu_st = "";
   String ak_st = "";
   imu_st += String(c,HEX);
   imu_st += "|";
   if (c == 0x71) {
     myIMU.MPU9250SelfTest(myIMU.SelfTest);
     imu_st += String(myIMU.SelfTest[0]);
     imu_st += "|";
     imu_st += String(myIMU.SelfTest[1]);
     imu_st += "|";
     imu_st += String(myIMU.SelfTest[2]);
     imu_st += "|";
     imu_st += String(myIMU.SelfTest[3]);
     imu_st += "|";
     imu_st += String(myIMU.SelfTest[4]);
     imu_st += "|";
     imu_st += String(myIMU.SelfTest[5]);

     myIMU.calibrateMPU9250(myIMU.gyroBias, myIMU.accelBias);
     myIMU.initMPU9250();
     byte d = myIMU.readByte(AK8963_ADDRESS, WHO_AM_I_AK8963);
     myIMU.initAK8963(myIMU.magCalibration);
     ak_st += String(d,HEX);
     ak_st += "|";
     ak_st += String(myIMU.magCalibration[0]);
     ak_st += "|";
     ak_st += String(myIMU.magCalibration[1]);
     ak_st += "|";
     ak_st += String(myIMU.magCalibration[2]);
   } // if (c == 0x71)
   else {
     Serial.print("Could not connect to MPU9250: 0x");
     Serial.println(c, HEX);
     ErrorLog.append("Could not connect to MPU9250");
     publish_wrapper(ERROR_EVENT, "Could not connect to MPU9250");
     //TODO send an error message out
   }
   return imu_st + "\n" + ak_st;
 }

String imu_loop() {
   // If intPin goes high, all data registers have new data
   // On interrupt, check if data ready interrupt
   if (myIMU.readByte(MPU9250_ADDRESS, INT_STATUS) & 0x01)
   {
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
    imu += "|";
    if (myIMU.delt_t > 1) //GO AS FAST AS POSSIBLE
    {
       String x_accel_mg = String(1000*myIMU.ax);
       imu += x_accel_mg;
       String y_accel_mg = String(1000*myIMU.ay);
       imu += "|";
       imu += y_accel_mg;
       String z_accel_mg = String(1000*myIMU.az);
       imu += "|";
       imu += z_accel_mg;
       String x_gyro_ds = String(myIMU.gx);
       imu += "|";
       imu += x_gyro_ds;
       String y_gyro_ds = String(myIMU.gy);
       imu += "|";
       imu += y_gyro_ds;
       String z_gyro_ds = String(myIMU.gz);
       imu += "|";
       imu += z_gyro_ds;
       String x_mag_mg = String(myIMU.mx);
       imu += "|";
       imu += x_mag_mg;
       String y_mag_mg = String(myIMU.my);
       imu += "|";
       imu += y_mag_mg;
       String z_mag_mg = String(myIMU.mz);
       imu += "|";
       imu += z_mag_mg;
       myIMU.tempCount = myIMU.readTempData();  // Read the adc values
       myIMU.temperature = ((float) myIMU.tempCount) / 333.87 + 21.0; // Temperature in degrees Centigrade
       String tmp_c = String(myIMU.temperature);
       imu += "|";
       imu += tmp_c;
       myIMU.count = millis();
    }
    return imu;
 }


 //***********************************
 //* Wrappers
 //***********************************
 //Particle.publish
void publish_wrapper(String tag, String message) {
  bool success;
  String time_str = String(Time.format(Time.now(), TIME_FORMAT_ISO8601_FULL));
  String to_publish = time_str + String("|") + message;
  success = Particle.publish(tag, to_publish);
  //TODO write to publish log
  cloud_publish_cnt = cloud_publish_cnt + 1;
}

void start_sample() {
  Serial.println("starting sample");
  sample_cnt = 0;
  SAMPLE_FLAG = true;
  //sample_poll_timer.start();
}

void take_a_sample() {
  Serial.println("sample");
  imu_loop();
}

void volDmp() {
    /*
  cout << F("\nVolume is FAT") << int(sd.vol()->fatType()) << endl;
  cout << F("blocksPerCluster: ") << int(sd.vol()->blocksPerCluster()) << endl;
  cout << F("clusterCount: ") << sd.vol()->clusterCount() << endl;
  cout << F("freeClusters: ");
  uint32_t volFree = sd.vol()->freeClusterCount();
  cout <<  volFree << endl;
  float fs = 0.000512*volFree*sd.vol()->blocksPerCluster();
  cout << F("freeSpace: ") << fs << F(" MB (MB = 1,000,000 bytes)\n");
  cout << F("fatStartBlock: ") << sd.vol()->fatStartBlock() << endl;
  cout << F("fatCount: ") << int(sd.vol()->fatCount()) << endl;
  cout << F("blocksPerFat: ") << sd.vol()->blocksPerFat() << endl;
  cout << F("rootDirStart: ") << sd.vol()->rootDirStart() << endl;
  cout << F("dataStartBlock: ") << sd.vol()->dataStartBlock() << endl;
  */

}

  //***********************************
  //* ye-old Arduino
  //***********************************
 void setup() {
   Serial.begin(9600);
   Serial.println("Initial Setup.");

   Wire.begin();
   System.on(all_events, handle_all_system_events);

   num_reboots = num_reboots + 1;
   powerCheck.setup();
   FuelGauge().quickStart();

   //cout << uppercase << showbase << endl;


   /*
   if (System.resetReason() == RESET_REASON_PANIC) {
       System.enterSafeMode();
   }
   */

   pinMode(debug_led_1, OUTPUT);
   pinMode(debug_led_2, OUTPUT);

   SD.setup();
   heartbeat.setup();

   Particle.variable("a", cloud_publish_cnt);
   Particle.variable("b", subscribe_cnt);
   Particle.variable("c", last_publish_time);
   Particle.variable("d", system_event_cnt);
   Particle.variable("e", last_system_event_time);
   Particle.variable("f", last_system_event_type);
   Particle.variable("h", sample_frequency);
   Particle.variable("i", imu_self_test_str);
   Particle.variable("k", charge_state_poll_frequency);
   Particle.variable("l", num_reboots);
   Particle.variable("v", String(System.version().c_str()));

   Particle.function("reboot",cloud_reboot);
   Particle.function("cs_freq",set_charge_state_poll_frequency);
   Particle.function("samp_freq",set_sample_poll_frequency);
   Particle.function("samp_buff",set_sample_buff);
   Particle.function("sd_reboot",cloud_function_sd_power_cycle);
   Particle.function("soc",get_soc);
   Particle.function("battv",get_battv);
   Particle.function("debug_sd", debug_sd);
   Particle.function("sample_test", sample_test);


   Particle.subscribe("pm_e_bus", handle_particle_event, MY_DEVICES);

   imu_self_test_str = self_test_imu();
   charge_state_poll_timer.start();

   init_sample_buffer();

   LEDStatus status;
   status.off();
   Particle.connect();

   //volDmp();
   Serial.println("Setup complete.");
 }

unsigned long lastCheck = 0;
char lastStatus[256];

void loop() {
  // Reboot the system
  if (RESET_FLAG) {
    Serial.println("reset");
    RESET_FLAG = false;
    System.reset();
  }

  // Allow particle to do any processing
  // https://docs.particle.io/reference/firmware/photon/#manual-mode
  if (Particle.connected()) {
    Particle.process();
  } else {
    // Don't attempt to connect too frequently as connection attempts hang MY_DEVICES
    static int last_connect_time = 0;
    const int connect_interval_sec = 60;
    int now = Time.now(); // unix time

    if ((last_connect_time == 0) || (now-last_connect_time > connect_interval_sec)) {
      last_connect_time = now;
      Particle.connect();
    }
  }

  if (CHARGE_STATE_FLAG) {
    Serial.println("charge_state");
    CHARGE_STATE_FLAG = false;
    String power_stats = String(FuelGauge().getSoC()) + String("|") + String(FuelGauge().getVCell()) + String("|") + String(powerCheck.getIsCharging());
    publish_wrapper(CHARGE_STATE_EVENT, CHARGE_STATE_MSG + String("|") + power_stats);
    ChargeStateLog.append(power_stats);
  }

  if (SAMPLE_FLAG) {
    if (sample_cnt >= sample_buff_size) {
        //sample_poll_timer.stop();
        SAMPLE_FLAG = false;
        Serial.println("ending sample");
        Serial.println(sample_buffer);
        SampleLog.append(sample_buffer);
        sample_buffer = "";
    } else {
        sample_cnt += 1;
        String res = imu_loop();
        sample_buffer = String(sample_buffer) + String(res) + String("\n");
    }
  }

  heartbeat.loop();

  if (SD_READ_FLAG) {
    Serial.println("sd read flag");
    SD_READ_FLAG = false;
    String sd_res = SD.Read(SD_LOG_NAME);
    publish_wrapper(SD_READ_EVENT,sd_res);
  }

  //Sync Time
  if (millis() - lastSync > ONE_DAY_MILLIS) {
    Serial.println("time_sync");
    Particle.syncTime();
    lastSync = millis();
    publish_wrapper(TIME_SYNC_EVENT, "");
    EventLog.append("time_sync");
  }


  //Call the automatic watchdog
  wd.checkin();
}
