/*
 * Project sensor_board_v2
 * Description: For Q2 2018 Deployment in Ghana
 * Author: Noah Klugman; Pat Pannuto
 */

#include <CellularHelper.h>
#include <google-maps-device-locator.h>
#include <Particle.h>

#include "ChargeState.h"
#include "Cloud.h"
#include "FileLog.h"
#include "Heartbeat.h"
#include "Imu.h"
#include "SDCard.h"
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
auto ErrorLog = FileLog(SD, "error_log.txt");
auto EventLog = FileLog(SD, "event_log.txt");
auto FunctionLog = FileLog(SD, "function_log.txt");
auto SampleLog = FileLog(SD, "sample_log.txt");
auto SubscriptionLog = FileLog(SD, "subscription_log.txt");

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
//* Heartbeat
//***********************************
retained int HEARTBEAT_FREQUENCY = Heartbeat::DEFAULT_FREQ;
retained int HEARTBEAT_COUNT = 0;
auto heartbeat = Heartbeat(SD, &HEARTBEAT_FREQUENCY, &HEARTBEAT_COUNT);

//***********************************
//* Charge state
//***********************************
retained int CHARGE_STATE_FREQUENCY = ChargeState::DEFAULT_FREQ;
auto charge_state = ChargeState(SD, &CHARGE_STATE_FREQUENCY);

//***********************************
//* IMU
//***********************************
retained int IMU_FREQUENCY = Imu::DEFAULT_FREQ;
retained int IMU_SAMPLE_COUNT = Imu::DEFAULT_SAMPLE_COUNT;
retained int IMU_SAMPLE_RATE = Imu::DEFAULT_SAMPLE_RATE_MS;
auto imu = Imu(SD, &IMU_FREQUENCY, &IMU_SAMPLE_COUNT, &IMU_SAMPLE_RATE);


//***********************************
//* Application State
//***********************************

//Loop switches
bool SAMPLE_FLAG = false;
bool SD_READ_FLAG = false;

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

 // imu
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

   //cout << uppercase << showbase << endl;


   /*
   if (System.resetReason() == RESET_REASON_PANIC) {
       System.enterSafeMode();
   }
   */

   pinMode(debug_led_1, OUTPUT);
   pinMode(debug_led_2, OUTPUT);

   SD.setup();
   resetSubsystem.setup();
   heartbeat.setup();
   charge_state.setup();
   imu.setup();

   Particle.variable("d", system_event_cnt);
   Particle.variable("e", last_system_event_time);
   Particle.variable("f", last_system_event_type);
   Particle.variable("l", num_reboots);
   Particle.variable("v", String(System.version().c_str()));

   Particle.function("sd_reboot",cloud_function_sd_power_cycle);
   Particle.function("soc",get_soc);
   Particle.function("battv",get_battv);
   Particle.function("debug_sd", debug_sd);

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

  resetSubsystem.loop();
  heartbeat.loop();
  charge_state.loop();
  imu.loop();

  if (SD_READ_FLAG) {
    Serial.println("sd read flag");
    SD_READ_FLAG = false;
    String sd_res = SD.Read(SD_LOG_NAME);
    Cloud::Publish(SD_READ_EVENT,sd_res);
  }

  //Sync Time
#define ONE_DAY_MILLIS (24 * 60 * 60 * 1000)
unsigned long lastSync = millis();
  if (millis() - lastSync > ONE_DAY_MILLIS) {
    Serial.println("time_sync");
    Particle.syncTime();
    lastSync = millis();
    Cloud::Publish(TIME_SYNC_EVENT, "");
    EventLog.append("time_sync");
  }


  //Call the automatic watchdog
  wd.checkin();
}
