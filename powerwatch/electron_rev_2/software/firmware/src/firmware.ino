/*
 * Project sensor_board_v2
 * Description: For Q2 2018 Deployment in Ghana
 * Author: Noah Klugman; Pat Pannuto
 */

// Native
#include <Particle.h>

// Third party libraries
#include <CellularHelper.h>
#include <google-maps-device-locator.h>

// Our code
#include "ChargeState.h"
#include "Cloud.h"
#include "FileLog.h"
#include "Gps.h"
#include "Heartbeat.h"
#include "Imu.h"
#include "SDCard.h"
#include "Subsystem.h"
#include "firmware.h"


//***********************************
//* TODO's
//***********************************
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
auto EventLog = FileLog(SD, "event_log.txt");

//GoogleMapsDeviceLocator locator;

//***********************************
//* Reset Monitor
//***********************************
class Reset: public Subsystem {
  typedef Subsystem super;
  using Subsystem::Subsystem;

  // Defer this to the `loop` method so the cloud will get the ACK
  bool reset_flag = false;

  String cloudFunctionName() { return "reset"; }
  int cloudCommand(String command) {
    // Escape hatch if things are really borked
    if (command == "hard") {
      System.reset();
    }
    reset_flag = true;
    return 0;
  }

public:
  void loop() {
    super::loop();
    if (reset_flag) {
      System.reset();
    }
  }
};

auto resetSubsystem = Reset(SD, "reset_log.txt");

//***********************************
//* Time Sync
//*
//* Particle synchronizes its clock when it first connects. Over time, it will
//* drift away from real time. This routine will re-sync local time.
//***********************************
const int TWELVE_HOURS = 1000 * 60 * 60 * 12;

class TimeSync: public Subsystem {
  typedef Subsystem super;
  using Subsystem::Subsystem;

  String cloudFunctionName() { return "timeSync"; }
  int cloudCommand(String command) {
    if (command == "now") {
      sync();
    }
    return 0;
  }

  void sync() {
    if (! Particle.syncTimePending()) { // if not currently syncing
      unsigned long now = millis();
      unsigned long last = Particle.timeSyncedLast();

      if ((now - last) > TWELVE_HOURS) { // been a while
        log.append("now " + String(now) + ", last sync " + String(last));
        Particle.syncTime(); // kick off a sync
      }
    }
  }

public:
  void loop() {
    sync();
  }
};

auto timeSyncSubsystem = TimeSync(SD, "time_sync.txt");

//***********************************
//* Heartbeat
//***********************************
retained int HEARTBEAT_FREQUENCY = Heartbeat::DEFAULT_FREQ;
retained int HEARTBEAT_COUNT = 0;
auto heartbeatSubsystem = Heartbeat(SD, &HEARTBEAT_FREQUENCY, &HEARTBEAT_COUNT);

//***********************************
//* Charge state
//***********************************
retained int CHARGE_STATE_FREQUENCY = ChargeState::DEFAULT_FREQ;
auto chargeStateSubsystem = ChargeState(SD, &CHARGE_STATE_FREQUENCY);

//***********************************
//* IMU
//***********************************
retained int IMU_FREQUENCY = Imu::DEFAULT_FREQ;
retained int IMU_SAMPLE_COUNT = Imu::DEFAULT_SAMPLE_COUNT;
retained int IMU_SAMPLE_RATE = Imu::DEFAULT_SAMPLE_RATE_MS;
auto imuSubsystem = Imu(SD, &IMU_FREQUENCY, &IMU_SAMPLE_COUNT, &IMU_SAMPLE_RATE);

//***********************************
//* GPS
//***********************************
retained int GPS_FREQUENCY = Gps::DEFAULT_FREQ;
auto gpsSubsystem = Gps(SD, &GPS_FREQUENCY);


//***********************************
//* Application State
//***********************************

//Loop switches
bool SAMPLE_FLAG = false;

String SD_LOG_NAME = "";

 //***********************************
 //* Cloud Publish
 //***********************************

 //publish wrapper types
 String SYSTEM_EVENT = "s";
 String SUBSCRIPTION_EVENT = "r";
 String TIME_SYNC_EVENT = "t";
 String CLOUD_FUNCTION_TEST_EVENT = "g";



 //***********************************
 //* Cloud Variables
 //***********************************

 // system events
 retained int system_event_cnt = 0;
 retained String last_system_event_time = "";
 retained int last_system_event_type = -999;
 retained int num_reboots = 0; //TODO

 String sample_buffer;
 retained int sample_cnt;

 //***********************************
 //* CLOUD FUNCTIONS
 //***********************************
 int get_soc(String c) { //cloudfunction
     return (int)(FuelGauge().getSoC());
 }

 int get_battv(String c) { //cloudfunction
     return (int)(100 * FuelGauge().getVCell());
 }



//System Events
void handle_all_system_events(system_event_t event, int param) {
  system_event_cnt = system_event_cnt + 1;
  Serial.printlnf("got event %d with value %d", event, param);
  String system_event_str = String((int)event) + "|" + String(param);
  String time_str = String(Time.format(Time.now(), TIME_FORMAT_ISO8601_FULL));
  last_system_event_time = time_str;
  last_system_event_type = param;
  Cloud::Publish(SYSTEM_EVENT, system_event_str);
  EventLog.append(system_event_str);
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
   FuelGauge().quickStart();

   /*
   if (System.resetReason() == RESET_REASON_PANIC) {
       System.enterSafeMode();
   }
   */

   pinMode(debug_led_1, OUTPUT);
   pinMode(debug_led_2, OUTPUT);

   // Setup SD card first so that other setups can log
   SD.setup();

   resetSubsystem.setup();
   timeSyncSubsystem.setup();
   heartbeatSubsystem.setup();
   chargeStateSubsystem.setup();
   imuSubsystem.setup();
   gpsSubsystem.setup();

   Particle.variable("d", system_event_cnt);
   Particle.variable("e", last_system_event_time);
   Particle.variable("f", last_system_event_type);
   Particle.variable("l", num_reboots);
   Particle.variable("v", String(System.version().c_str()));

   Particle.function("soc",get_soc);
   Particle.function("battv",get_battv);

   LEDStatus status;
   status.off();
   Particle.connect();

   Serial.println("Setup complete.");
 }

unsigned long lastCheck = 0;
char lastStatus[256];

void loop() {
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

  SD.loop();
  resetSubsystem.loop();
  timeSyncSubsystem.loop();
  heartbeatSubsystem.loop();
  chargeStateSubsystem.loop();
  imuSubsystem.loop();
  gpsSubsystem.loop();


  //Call the automatic watchdog
  wd.checkin();
}
