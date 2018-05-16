/*
 * Project sensor_board_v2
 * Description: For Q2 2018 Deployment in Ghana
 * Author: Noah Klugman; Pat Pannuto
 */

// Native
#include <Particle.h>

// Third party libraries
#include <APNHelperRK.h>
#include <CellularHelper.h>
#include <google-maps-device-locator.h>
#include <OneWire.h>

// Our code
#include "CellStatus.h"
#include "ChargeState.h"
#include "Cloud.h"
#include "ESP8266.h"
#include "FileLog.h"
#include "Gps.h"
#include "Imu.h"
#include "Light.h"
#include "NrfWit.h"
#include "SDCard.h"
#include "Subsystem.h"
#include "Timesync.h"
#include "Wifi.h"
#include "firmware.h"


//***********************************
//* TODO's
//***********************************

//System
//  State Machine refactor
//    remove timers?
//    remove threads
//    think about on-event actions (do we do at all? do we keep a buffer?)
//  Merge in Matt's branch
//  Sanity check cloud commands // controls. Minimize them. Document.
//  Integrate with the publish manager (maybe not a concern anymore?)
//  Figure out what to do with system events
//  figure out when to go to safe mode
//  control the debug light
//  add in periodic reset
//Peripherals
//  add temp alone
//  make wifi syncronus
//  wifi ssid cloud hashing
//  write audio transfer
//  APN libraries / switches
//  Add unique ID chip
//SD
//  add delete (maybe not?)
//  add format (maybe not?)
//  check powercycle
//  add upload file, upload all
//Default Heartbeat Packet
//  size of free mem on SD Card (maybe not?)
//  system mem
//  temp
//  cellular strength
//  system count (retained)
//  software version (maybe not?)
//  isCharging
//  num heartbeat (retained)
//Identity Packet (daily?)
//  IMEI
//  ICCID
//  Cape ID
//  SD Card name
//  Last GPS
//  WiT MAC
//  System Count
//Heartbeat Stretch goals
//  SMS Heartbeat
//    disable on particle apn
//    figure out endpoint
//  Heartbeat on certain system events (upgrade in specific)
//Tests
//  SD works without cellular
//  Cellular works without SD
//  Device comes back from dead battery
//  Cross validate wits, temp, light, audio?
//Random ideas
//  Can we run on 0.8.0?

//***********************************
//* Critical System Config
//***********************************
int version_num = 2; //hack
PRODUCT_ID(7456); //US testbed
PRODUCT_VERSION(5);
SYSTEM_THREAD(ENABLED);
STARTUP(System.enableFeature(FEATURE_RESET_INFO));
STARTUP(System.enableFeature(FEATURE_RETAINED_MEMORY));
//ArduinoOutStream cout(Serial);
//STARTUP(cellular_credentials_set("http://mtnplay.com.gh", "", "", NULL));
SYSTEM_MODE(MANUAL);
bool handshake_flag = false;
OneWire ds(B0);

//**********************************
//* Pin Configuration
//**********************************
int reset_btn = A0;


//***********************************
//* Watchdogs
//***********************************
const int HARDWARE_WATCHDOG_TIMEOUT_MS = 1000 * 60;
ApplicationWatchdog wd(HARDWARE_WATCHDOG_TIMEOUT_MS, soft_watchdog_reset);
retained int system_cnt;

//***********************************
//* SD Card
//***********************************
SDCard SD;

//***********************************
//* ESP8266 WIFI Module
//***********************************
String serial5_response;
bool serial5_recv_done;
auto esp8266 = ESP8266(&serial5_response, &serial5_recv_done);

//***********************************
//* Timesync
//***********************************
auto timeSyncSubsystem = Timesync();

//***********************************
//* CellStatus
//***********************************
auto cellStatus = CellStatus();

//***********************************
//* Charge state
//***********************************
auto chargeStateSubsystem = ChargeState();

//***********************************
//* IMU
//***********************************
retained int IMU_MOTION_THRESHOLD = Imu::DEFAULT_MOTION_THRESHOLD;
auto imuSubsystem = Imu(&IMU_MOTION_THRESHOLD);

//***********************************
//* LIGHT
//***********************************
auto lightSubsystem = Light();

//***********************************
//* NrfWit
//***********************************
auto nrfWitSubsystem = NrfWit();

//***********************************
//* WIFI
//***********************************
auto wifiSubsystem = Wifi(esp8266, &serial5_response, &serial5_recv_done);

//***********************************
//* GPS
//***********************************
auto gpsSubsystem = Gps();

//***********************************
//* System Events
//***********************************
auto EventLog = FileLog(SD, "event_log.txt");
std::queue<String> EventQueue;
std::queue<String> CloudQueue;

//***********************************
//* System Data
//***********************************
auto DataLog = FileLog(SD, "data_log.txt");

// String SYSTEM_EVENT = "s";
retained int system_event_count = 0;
retained String last_system_event_time = "";
retained int last_system_event_type = -999;
retained int num_reboots = 0;
retained int num_manual_reboots = 0;

void handle_all_system_events(system_event_t event, int param) {
  system_event_count++;
  // cast as per BDub post here: https://community.particle.io/t/system-events-param-problem/32071/14
  Serial.printlnf("got event H: %lu event L: %lu with value %d", (uint32_t)(event>>32), (uint32_t)event, param);
  String system_event_str = String((int)event) + "|" + String(param);
  String time_str = String(Time.format(Time.now(), TIME_FORMAT_ISO8601_FULL));
  last_system_event_time = time_str;
  last_system_event_type = param;

  //Push this system event onto the queue to be logged in the error logging state
  EventQueue.push(time_str + ": " + system_event_str);
}

void handle_error(String error, bool cloud) {
  Serial.printlnf("Got error: %s", error);
  String time_str = String(Time.format(Time.now(), TIME_FORMAT_ISO8601_FULL));
  last_system_event_time = time_str;

  //Push this system event onto the queue to be logged in the error logging state
  if(cloud) {
      CloudQueue.push(time_str + ": " + error);
  }

  EventQueue.push(time_str + ": " + error);
}

void system_reset_to_safemode() {
  num_manual_reboots++;
  // Commenting out this should be logged as a system event
  // Cloud::Publish(SYSTEM_EVENT, "manual reboot"); //TODO test if this hangs
  System.enterSafeMode();
}

int force_handshake(String cmd) {
  handshake_flag = true;
  return 0;
}

// The loop will act as a state machine. Certain particle calls are called every
// loop then states are executed in order. The following enumeration defines
// the states. Each state has a timeout. On timeout we reset the Particle
// and log that state as an error, moving on to the next state.
enum SystemState {
  CheckCloudEvent,
  CheckTimeSync,
  SenseChargeState,
  SenseMPU,
  SenseWiFi,
  SenseCell,
  SenseSDPresent,
  SenseLight,
  SenseWit,
  SenseGPS,
  LogPacket,
  SendPacket,
  LogError,
  SendError,
  Wait
};

SystemState nextState(SystemState s) {
    return static_cast<SystemState>(static_cast<int>(s) + 1);
}

enum ParticleCloudState {
  ConnectionCheck,
  UpdateCheck,
  HandshakeCheck
};

ParticleCloudState cloudState = ConnectionCheck;

// Retained system states are used to diagnose restarts (error vs hard reset)
retained SystemState state = Wait;
retained SystemState lastState = SendError;

const APNHelperAPN apns[1] = {
  {"8901260", "wireless.twilio.com"}
};
APNHelper apnHelper(apns, sizeof(apns)/sizeof(apns[0]));

//***********************************
//* ye-old Arduino
//***********************************
void setup() {
  // if (System.resetReason() == RESET_REASON_PANIC) {
  //   System.enterSafeMode();
  // }

  //setup the apns
  apnHelper.setCredentials();

  //This function tells the particle to force a reconnect with the cloud
  Particle.function("handshake", force_handshake);

  // Set up debugging UART
  Serial.begin(9600);
  Serial.println("Initial Setup.");

  // Set up I2C
  Wire.begin();

  // For now, just grab everything that happens and log about it
  // https://docs.particle.io/reference/firmware/photon/#system-events
  System.on(all_events, handle_all_system_events);

  // Get the reset button going
  pinMode(reset_btn, INPUT);
  attachInterrupt(reset_btn, system_reset_to_safemode, RISING, 3);

  // Setup SD card first so that other setups can log
  SD.setup();

  timeSyncSubsystem.setup();
  chargeStateSubsystem.setup();
  imuSubsystem.setup();
  lightSubsystem.setup();
  nrfWitSubsystem.setup();
  gpsSubsystem.setup();
  wifiSubsystem.setup();
  FuelGauge().quickStart();

  LEDStatus status;
  status.off();

  Particle.connect();

  // If our state and lastState is the same we got stuck in a
  // state and didn't transtition
  if(state == lastState) {
    String err_str(state);
    handle_error("Reset after stuck in state " + err_str, true);

    // If we hung on the last iteration, move on to the next state
    state = nextState(state);
  }


  Serial.println("Setup complete.");
}


//State Timer is reused to make sure a state doesn't loop for too long.
//If it loops for too long we just call a reset. This can get us out of
//liveness bugs in a specific driver
Timer stateTimer(60000,soft_watchdog_reset,true);

//Before calling each state's loop function the state should call this
//function with a period of the maximum time the state is allowed to take
void manageStateTimer(unsigned int period) {
  if(state != lastState) {
    Serial.printlnf("Transitioning to state %d from %d", state, lastState);
    stateTimer.stop();
    stateTimer.changePeriod(period);
    stateTimer.reset();
    stateTimer.start();
    lastState = state;
  }
}

//This structure is what all of the drivers will return. It will
//be packetized and send to the cloud in the sendPacket state
#define RESULT_LEN 80
struct ResultStruct {
  char chargeStateResult[RESULT_LEN];
  char mpuResult[RESULT_LEN];
  char wifiResult[RESULT_LEN];
  char cellResult[RESULT_LEN];
  char sdStatusResult[RESULT_LEN];
  char lightResult[RESULT_LEN];
  char witResult[RESULT_LEN];
  char gpsResult[RESULT_LEN];
};

// A function to clear all the fields of a resultStruct
void clearResults(ResultStruct* r) {
  r->chargeStateResult[0] = 0;
  r->mpuResult[0] = 0;
  r->wifiResult[0] = 0;
  r->cellResult[0] = 0;
  r->sdStatusResult[0] = 0;
  r->lightResult[0] = 0;
  r->witResult[0] = 0;
  r->gpsResult[0] = 0;
}

// A function to take all of the resutl strings and concatenate them together
String stringifyResults(ResultStruct r) {
  String result = "";
  result += String(r.chargeStateResult);
  result += MAJOR_DLIM;
  result += String(r.mpuResult);
  result += MAJOR_DLIM;
  result += String(r.wifiResult);
  result += MAJOR_DLIM;
  result += String(r.cellResult);
  result += MAJOR_DLIM;
  result += String(r.sdStatusResult);
  result += MAJOR_DLIM;
  result += String(r.lightResult);
  result += MAJOR_DLIM;
  result += String(r.witResult);
  result += MAJOR_DLIM;
  result += String(r.gpsResult);
  return result;
}

// retain this so that on the next iteration we still get results on hang
retained ResultStruct sensingResults;

void loop() {
  // Allow particle to do any processing
  // https://docs.particle.io/reference/firmware/photon/#manual-mode

  // This is the only thing that will happen outside of the state machine!
  // Everything else, including reconnection attempts and cloud update Checks
  // Should happen in the cloud event state
  static bool once = false;
  if (Particle.connected()) {
    Particle.process();

    // We need to set up the keepalive the first time the particle becomes
    // connected
    if(!once) {
      Particle.keepAlive(30); // send a ping every 30 seconds
      once = true;
    }
  }

  switch(state) {
    case CheckCloudEvent: {
      manageStateTimer(120000);

      switch(cloudState) {
        int particle_connect_time;

        case ConnectionCheck: {
          //Check if we have a connection - if it's been a while attempt to reconnect
          if(!Particle.connected()) {
            // Don't attempt to connect too frequently as connection attempts hang MY_DEVICES
            static int last_connect_time = 0;
            const int connect_interval_sec = 60;
            int now = Time.now(); // unix time

            if ((last_connect_time == 0) || (now-last_connect_time > connect_interval_sec)) {
              last_connect_time = now;
              Particle.connect();
            }
          }
          cloudState = UpdateCheck;
          particle_connect_time = millis();
          break;
        }

        case UpdateCheck: {
          if (System.updatesPending()) {
            //Spend a minute just trying to fetch the update
            if(millis() - particle_connect_time < 60000) {
              Particle.process();
            } else {
              cloudState = HandshakeCheck;
            }
          } else {
            cloudState = HandshakeCheck;
          }
          break;
        }

        case HandshakeCheck: {
          if (handshake_flag) {
            handshake_flag = false;
            Particle.publish("spark/device/session/end", "", PRIVATE);
          }
          cloudState = ConnectionCheck;
          state = nextState(state);
          break;
        }
      }
      break;
    }

    case CheckTimeSync: {
      manageStateTimer(180000);

      LoopStatus result = timeSyncSubsystem.loop();

      //return result or error
      if(result == FinishedError) {
        //Log the error in the error struct
        handle_error("timeSync error", false);
        state = nextState(state);
      } else if(result == FinishedSuccess) {
        //get the result from the charge state and put it into the system struct
        state = nextState(state);
      }
      break;
    }

    case SenseChargeState: {
      //It should not take more than 1s to sense charge state
      manageStateTimer(1000);

      //Call the loop function for sensing charge state
      LoopStatus result = chargeStateSubsystem.loop();

      //return result or error
      if(result == FinishedError) {
        //Log the error in the error struct
        handle_error("chargeState error", false);
        strncpy(sensingResults.chargeStateResult, "!", RESULT_LEN-1);
        state = nextState(state);
      } else if(result == FinishedSuccess) {
        //get the result from the charge state and put it into the system struct
        strncpy(sensingResults.chargeStateResult, chargeStateSubsystem.getResult().c_str(), RESULT_LEN-1);
        state = nextState(state);
      }
      break;
    }

    case SenseMPU: {
      //It should not take more than 10s to check the IMU
      manageStateTimer(10000);

      LoopStatus result = imuSubsystem.loop();

      //return result or error
      if(result == FinishedError) {
        //Log the error in the error struct
        handle_error("IMU error", false);
        strncpy(sensingResults.mpuResult, "!", RESULT_LEN-1);
        state = nextState(state);
      } else if(result == FinishedSuccess) {
        //get the result from the charge state and put it into the system struct
        strncpy(sensingResults.mpuResult, imuSubsystem.getResult().c_str(), RESULT_LEN-1);
        state = nextState(state);
      }
      break;
    }

    case SenseWiFi: {
      //It might take a while to get all of the wifi SSIDs
      manageStateTimer(20000);

      LoopStatus result = wifiSubsystem.loop();

      //return result or error
      if(result == FinishedError) {
        //Log the error in the error struct
        handle_error("WiFi error", false);
        strncpy(sensingResults.wifiResult, "!", RESULT_LEN-1);
        state = nextState(state);
      } else if(result == FinishedSuccess) {
        //get the result from the charge state and put it into the system struct
        strncpy(sensingResults.wifiResult, wifiSubsystem.getResult().c_str(), RESULT_LEN-1);
        state = nextState(state);
      }
      break;
    }

    case SenseCell: {
      manageStateTimer(5000);

      LoopStatus result = cellStatus.loop();

      //return result or error
      if(result == FinishedError) {
        //Log the error in the error struct
        handle_error("cellStatus error", false);
        strncpy(sensingResults.cellResult, "!", RESULT_LEN-1);
        state = nextState(state);
      } else if(result == FinishedSuccess) {
        //get the result from the charge state and put it into the system struct
        strncpy(sensingResults.cellResult, cellStatus.getResult().c_str(), RESULT_LEN-1);
        state = nextState(state);
      }
      break;
    }

    case SenseSDPresent: {
      //This should just be a GPIO pin
      manageStateTimer(1000);

      LoopStatus result = SD.loop();

      //return result or error
      if(result == FinishedError) {
        //Log the error in the error struct
        handle_error("cellStatus error", false);
        strncpy(sensingResults.sdStatusResult, "!", RESULT_LEN-1);
        state = nextState(state);
      } else if(result == FinishedSuccess) {
        //get the result from the charge state and put it into the system struct
        strncpy(sensingResults.sdStatusResult, SD.getResult().c_str(), RESULT_LEN-1);
        state = nextState(state);
      }
      break;
    }

    case SenseLight: {
      manageStateTimer(1000);

      LoopStatus result = lightSubsystem.loop();

      //return result or error
      if(result == FinishedError) {
        //Log the error in the error struct
        handle_error("light error", false);
        strncpy(sensingResults.lightResult, "!", RESULT_LEN-1);
        state = nextState(state);
      } else if(result == FinishedSuccess) {
        //get the result from the charge state and put it into the system struct
        strncpy(sensingResults.lightResult, lightSubsystem.getResult().c_str(), RESULT_LEN-1);
        state = nextState(state);
      }
      break;
    }

    case SenseWit: {
      //This requires scanning for an advertisement
      manageStateTimer(20000);

      LoopStatus result = nrfWitSubsystem.loop();

      //return result or error
      if(result == FinishedError) {
        //Log the error in the error struct
        handle_error("nrfWit error", false);
        strncpy(sensingResults.witResult, "!", RESULT_LEN-1);
        state = nextState(state);
      } else if(result == FinishedSuccess) {
        //get the result from the charge state and put it into the system struct
        strncpy(sensingResults.witResult, nrfWitSubsystem.getResult().c_str(), RESULT_LEN-1);
        state = nextState(state);
      }
      break;
    }

    case SenseGPS: {
      manageStateTimer(1000);
      LoopStatus result = gpsSubsystem.loop();

      //return result or error
      if(result == FinishedError) {
        //Log the error in the error struct
        handle_error("GPS error", false);
        state = nextState(state);
      } else if(result == FinishedSuccess) {
        //get the result from the charge state and put it into the system struct
        strncpy(sensingResults.gpsResult, gpsSubsystem.getResult().c_str(), RESULT_LEN-1);
        state = nextState(state);
      }
      break;
    }

    case LogPacket: {
      manageStateTimer(30000);

      SD.PowerOn();
      String packet = stringifyResults(sensingResults);
      if(DataLog.append(packet)) {
        handle_error("Data logging error", true);
      }
      SD.PowerOff();
      state = nextState(state);
      break;
    }

    case SendPacket: {
      manageStateTimer(10000);

      String packet = stringifyResults(sensingResults);
      if(!Cloud::Publish("g",packet)) {
        handle_error("Data publishing error", true);
      }
      state = nextState(state);
      break;
    }

    case LogError: {
      manageStateTimer(30000);
      static int count = 0;

      SD.PowerOn();
      if(!EventQueue.empty() && count < 4) {
        count++;
        if(EventLog.append(EventQueue.front())) {
          handle_error("Event logging error", true);
        } else {
          EventQueue.pop();
        }
      } else {
        SD.PowerOff();
        state = nextState(state);
      }

      break;
    }

    case SendError: {
      manageStateTimer(30000);
      static int count = 0;

      if(!CloudQueue.empty() && count < 4) {
        count++;
        Cloud::Publish("!",CloudQueue.front());
        CloudQueue.pop();
      } else {
        state = nextState(state);
        count = 0;
      }

      break;
    }

    case Wait: {
      manageStateTimer(1200000);

      static bool first = false;
      static int mill = 0;
      if(!first) {
        mill = millis();
        first = true;
      }

      if(millis() - mill > 600000) {
        clearResults(&sensingResults);
        state = CheckCloudEvent;
        first = false;

      }
      break;
    }
  }

  //Call the automatic watchdog
  wd.checkin();

  system_cnt++;
}

void soft_watchdog_reset() {
  //reset_flag = true; //let the reset subsystem shutdown gracefully
  //TODO change to system reset after a certain number of times called
  System.reset();
}

void id() {
  byte i;
  boolean present;
  byte data[8];     // container for the data from device
  byte crc_calc;    //calculated CRC
  byte crc_byte;    //actual CRC as sent by DS2401
  //1-Wire bus reset, needed to start operation on the bus,
  //returns a 1/TRUE if presence pulse detected
  present = ds.reset();
  if (present == TRUE)
  {
    ds.write(0x33);  //Send Read data command
    data[0] = ds.read();
    Serial.print("Family code: 0x");
    PrintTwoDigitHex (data[0], 1);
    Serial.print("Hex ROM data: ");
    for (i = 1; i <= 6; i++)
    {
      data[i] = ds.read(); //store each byte in different position in array
      PrintTwoDigitHex (data[i], 0);
      Serial.print(" ");
    }
    Serial.println();
    crc_byte = ds.read(); //read CRC, this is the last byte
    crc_calc = OneWire::crc8(data, 7); //calculate CRC of the data
    Serial.print("Calculated CRC: 0x");
    PrintTwoDigitHex (crc_calc, 1);
    Serial.print("Actual CRC: 0x");
    PrintTwoDigitHex (crc_byte, 1);
  }
  else //Nothing is connected in the bus
  {
    Serial.println("xxxxx Nothing connected xxxxx");
  }
}


void PrintTwoDigitHex (byte b, boolean newline)
{
  Serial.print(b/16, HEX);
  Serial.print(b%16, HEX);
  if (newline) Serial.println();
}
