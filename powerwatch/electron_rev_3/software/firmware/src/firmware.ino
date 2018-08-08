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
#include "uCommand.h"
#include "Wifi.h"
#include "firmware.h"
#include "BatteryCheck.h"

//***********************************
//* TODO's
//***********************************

//  control the debug light
// Default Heartbeat Packet
//  WiT MAC
// Heartbeat Stretch goals
//  SMS Heartbeat
//Tests
//  Device comes back from dead battery

//***********************************
//* Critical System Config
//***********************************
int version_num = 2; //hack
PRODUCT_ID(7233); //VZ
int product_id = 7233;
PRODUCT_VERSION(25);
int version_int = 25;
SYSTEM_THREAD(ENABLED);
STARTUP(System.enableFeature(FEATURE_RESET_INFO));
STARTUP(System.enableFeature(FEATURE_RETAINED_MEMORY));
SYSTEM_MODE(MANUAL);
bool handshake_flag = false;
OneWire ds(B0);
String id(void);
String shield_id = "";

//**********************************
//* Pin Configuration
//**********************************
int reset_btn = A0;

//***********************************
//* Watchdogs
//***********************************
const int HARDWARE_WATCHDOG_TIMEOUT_MS = 1000 * 60;
ApplicationWatchdog wd(HARDWARE_WATCHDOG_TIMEOUT_MS, soft_watchdog_reset);
unsigned long system_cnt = 0;
retained unsigned long reboot_cnt = 0;
retained unsigned long sd_cnt = 0;

//***********************************
//* SD Card
//***********************************
SDCard SD;

//***********************************
//* ESP8266 WIFI Module
//***********************************
auto esp8266 = ESP8266();

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
//* uCommand
//***********************************
uCommand uCmd;

//***********************************
//* WIFI
//***********************************
auto wifiSubsystem = Wifi(esp8266);

//***********************************
//* GPS
//***********************************
auto gpsSubsystem = Gps();

//***********************************
//* System Events
//***********************************
retained char event_log_name[50];
auto EventLog = FileLog(SD, "event_log.txt", event_log_name);
std::queue<String> EventQueue;
std::queue<String> CloudQueue;
std::deque<String> DataDeque;

//***********************************
//* Battery check
//***********************************
BatteryCheck batteryCheck(5, 60);
unsigned long last_cloud_event = 0;

//***********************************
//* System Data
//***********************************
retained char data_log_name[50];
auto DataLog = FileLog(SD, "data_log.txt", data_log_name);
unsigned long last_logging_event  = 0;

//***********************************
//* UDP
//***********************************
//UDP udp;


// String SYSTEM_EVENT = "s";
retained int system_event_count = 0;
retained String last_system_event_time = "";
retained int last_system_event_type = -999;
retained int num_reboots = 0;
retained int num_manual_reboots = 0;

volatile int network_state = 0;
volatile int cloud_state = 0;

void handle_all_system_events(system_event_t event, int param) {
  system_event_count++;
  // cast as per BDub post here: https://community.particle.io/t/system-events-param-problem/32071/14
  if((uint32_t)event == 64) {
    cloud_state = param;
  }

  if((uint32_t)event == 32) {
    network_state = param;
  }

  Serial.printlnf("got event H: %lu event L: %lu with value %d", (uint32_t)(event>>32), (uint32_t)event, param);
  String system_event_str = String((int)event) + "|" + String(param);
  String time_str = String(Time.format(Time.now(), TIME_FORMAT_ISO8601_FULL));
  last_system_event_time = time_str;
  last_system_event_type = param;

  //Push this system event onto the queue to be logged in the error logging state
  if(EventQueue.size() < 200) {
    EventQueue.push(time_str + ": " + system_event_str);
  }
}

void handle_error(String error, bool cloud) {
  Serial.printlnf("Got error: %s", error.c_str());
  String time_str = String(Time.format(Time.now(), TIME_FORMAT_ISO8601_FULL));
  last_system_event_time = time_str;

  //Push this system event onto the queue to be logged in the error logging state
  if(cloud) {
    if(CloudQueue.size() < 200) {
      CloudQueue.push(time_str + ": " + error);
    }
  }

  if(EventQueue.size() < 200) {
    EventQueue.push(time_str + ": " + error);
  }
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
  UpdateSystemStat,
  LogPacket,
  SendPacket,
  SendUDP,
  LogError,
  SendError,
  //CheckSMS,
  Wait
};

SystemState nextState(SystemState s) {
    return static_cast<SystemState>(static_cast<int>(s) + 1);
}

enum ParticleCloudState {
  ParticleConnectionCheck,
  CellularConnectionCheck,
  HandshakeCheck
};

ParticleCloudState cloudState = ParticleConnectionCheck;

// Retained system states are used to diagnose restarts (error vs hard reset)
retained SystemState state = CheckCloudEvent;
retained SystemState lastState = Wait;

const APNHelperAPN apns[2] = {
  {"8901260", "wireless.twilio.com"},
  {"8923301", "http://mtnplay.com.gh"}
};
APNHelper apnHelper(apns, sizeof(apns)/sizeof(apns[0]));

void reset_helper() {
  reset_state("");
}

int reset_state(String cmd) {
  state = CheckCloudEvent;
  lastState = Wait;
  System.reset();
}

//***********************************
//* ye-old Arduino
//***********************************
void setup() {
  // The first thing we do is to check the battery
  batteryCheck.checkAndSleepIfNecessary();

  // Keep track of reboots
  reboot_cnt++;

  //setup the apns
  apnHelper.setCredentials();

  //This function tells the particle to force a reconnect with the cloud
  Particle.function("handshake", force_handshake);
  Particle.function("reset_state", reset_state);

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
  attachInterrupt(reset_btn, reset_helper, RISING, 3);

  // Setup SD card first so that other setups can log
  SD.setup();

  //setup the other subsystems
  timeSyncSubsystem.setup();
  chargeStateSubsystem.setup();
  imuSubsystem.setup();
  lightSubsystem.setup();
  nrfWitSubsystem.setup();
  gpsSubsystem.setup();
  wifiSubsystem.setup();
  FuelGauge().quickStart();
  shield_id = id();

  //Turn off the audio front end section
  pinMode(B4, OUTPUT);
  digitalWrite(B4, LOW);

  // Enable the other power pins to outputs and set them high for now
  // NRF
  pinMode(B5, OUTPUT);
  digitalWrite(B5, HIGH);
  // GPS
  pinMode(D3, OUTPUT);
  digitalWrite(D3, HIGH);
  // SD
  SD.PowerOff();

  if(uCmd.setSMSMode(1) == RESP_OK) {
    Serial.println("Set up SMS mode");
  } else {
    handle_error("SMS Mode failed", false);
  }

  LEDStatus status;
  status.off();


  // If our state and lastState is the same we got stuck in a
  // state and didn't transtition
  if(state == lastState) {
    String err_str(state);
    handle_error("Reset after stuck in state " + err_str, true);

    // If we hung on the last iteration, move on to the next state
    state = nextState(state);
  } else {
    // If we reset and the states aren't the same then we didn't get stuck
    // I don't know what state we're in but go back to start
    state = CheckCloudEvent;
    lastState = Wait;
  }

  Serial.println("Setup complete.");
}


//State Timer is reused to make sure a state doesn't loop for too long.
//If it loops for too long we just call a reset. This can get us out of
//liveness bugs in a specific driver
Timer stateTimer(60000,soft_watchdog_reset,true);

//Before calling each state's loop function the state should call this
//function with a period of the maximum time the state is allowed to take
void manageStateTimer(unsigned long period) {
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
  char systemStat[RESULT_LEN];
  char SDstat[RESULT_LEN];
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
  r->systemStat[0] = 0;
  r->SDstat[0] = 0;
}

// A function to take all of the resutl strings and concatenate them together
String stringifyResults(ResultStruct r) {
  String result = "";
  result += String(Time.now());
  result += MINOR_DLIM;
  result += String(millis());
  result += MAJOR_DLIM;
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
  result += MAJOR_DLIM;
  result += String(r.systemStat);
  result += MAJOR_DLIM;
  result += String(r.SDstat);
  return result;
}

// retain this so that on the next iteration we still get results on hang
retained ResultStruct sensingResults;

void loop() {

  // This is the only thing that will happen outside of the state machine!
  // Everything else, including reconnection attempts and cloud update Checks
  // Should happen in the cloud event state

  // If we haven't reset in a day just reset
  if(millis() > 86400000) {
    reset_helper();
  }


  // If we connected call particle process for cloud events
  static bool once = false;
  if(!once) {
    Particle.keepAlive(30); // send a ping every 30 seconds
    once = true;
  }

  Particle.process();

  switch(state) {
    case CheckCloudEvent: {
      manageStateTimer(240000);


      switch(cloudState) {
        // First try to connect to the particle cloud for 60s
        case ParticleConnectionCheck: {
          static bool first = true;
          static unsigned long now;
          if(first) {
            //Check the battery!
            batteryCheck.checkAndSleepIfNecessary();
            Serial.println("Connecting to particle cloud");
            Particle.connect();
            now = millis();
          }
          first = false;

          //Try to connect for 60s
          if(millis() - now < 60000) {
              if(Particle.connected()) {
                Serial.println("Connected to particle cloud");
                first = true;
                cloudState = HandshakeCheck;
              }
          } else {
              //We failed to connect to the cloud - try to connect to the cellular network
              Serial.println("Particle cloud connection failed");
              //Stop trying to connect to the cloud
              Particle.disconnect();
               //0 is the disconnected state
              while(cloud_state != 0) {
                Particle.process();
              };

              //Turn the cellular modem off
              //Cellular.off();
              //while(Cellular.ready());
              //Cellular.on();
              first = true;
              cloudState = CellularConnectionCheck;
          }
          break;
        }

        case CellularConnectionCheck: {
          static bool first = true;
          static unsigned long now;
          if(first) {
            Serial.println("Connecting to cellular network");
            Cellular.connect();
            now = millis();
          }
          first = false;

          if(millis() - now < 60000) {
              if(Cellular.ready()) {
                Serial.println("Connected to cellular network");
                delay(5000);
                first = true;
                cloudState = HandshakeCheck;
              }
          } else {
              Serial.println("Cellular connection failed");
              first = true;
              cloudState = HandshakeCheck;
          }
          break;
        }

        case HandshakeCheck: {
          if (handshake_flag) {
            handshake_flag = false;
            Particle.publish("spark/device/session/end", "", PRIVATE);
          }
          cloudState = ParticleConnectionCheck;
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
      manageStateTimer(30000);

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
      manageStateTimer(20000);

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
      manageStateTimer(30000);
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

    case UpdateSystemStat: {
      manageStateTimer(1000);
      snprintf(sensingResults.systemStat, RESULT_LEN-1, "%lu|%s|%u", system_cnt, shield_id.c_str(), reboot_cnt);
      state = nextState(state);
      break;
    }

    case LogPacket: {
      manageStateTimer(40000);

      SD.PowerOn();

      //We should get the sd stat before logging the packet to the sd card
      int size = DataLog.getFileSize();
      if(size == -1) {
        handle_error("Data logging size error", false);
      } else {
        snprintf(sensingResults.SDstat, RESULT_LEN-1, "%d|%d|%s", sd_cnt, size, DataLog.getCurrentName().c_str());
      }

      String packet = stringifyResults(sensingResults);
      if(DataLog.append(packet)) {
        handle_error("Data logging error", true);
      } else {
        sd_cnt++;
        last_logging_event = millis();
      }

      SD.PowerOff();
      state = nextState(state);
      break;
    }

    case SendPacket: {
      manageStateTimer(40000);

      static int count = 0;

      if(count == 0) {
        String packet = stringifyResults(sensingResults);

        // Add the packet to the data queue
        if(DataDeque.size() < 200) {
          DataDeque.push_front(packet);
        } else {
          DataDeque.pop_back();
          DataDeque.push_front(packet);
        }
      }
      count++;


      Serial.printlnf("Data Queue size %d",DataDeque.size());

      if(Particle.connected()) {
        if(!DataDeque.empty() && count < 5) {

          Serial.printlnf("Sending data - size %d",DataDeque.size());
          String toSend = DataDeque.front();

          if(toSend.length() > 240) {
            if(!Cloud::Publish("g",toSend.substring(0,240))) {
              handle_error("Data publishing error", true);
            } else {
              if(!Cloud::Publish("g",toSend.substring(240))) {
                handle_error("Data publishing error", true);
              } else {
                DataDeque.pop_front();
              }
            }
          } else {
            if(!Cloud::Publish("g",toSend)) {
              handle_error("Data publishing error", true);
            } else {
              DataDeque.pop_front();
              last_cloud_event = millis();
            }
          }
        } else {
          count = 0;
          state = nextState(state);
        }

      } else {
        handle_error("Data publishing error", true);
        count = 0;
        state = nextState(state);
      }

      break;
    }

    case SendUDP: {
      manageStateTimer(40000);

      static int count = 0;
      count++;

      Serial.printlnf("Data Queue size %d",DataDeque.size());

      if(Cellular.ready()) {
        UDP udp;

        if(udp.begin(8888) != true) {
          Serial.println("UDP Begin error");
        }

        if(!DataDeque.empty() && count < 5) {

          Serial.printlnf("Sending data - size %d",DataDeque.size());
          String toSend = DataDeque.front();

          //construct some json
          String data = "\"data\": \"" + toSend + "\", ";
          String version = "\"version\": " + String(version_int) + ", ";
          String product = "\"productID\": " + String(product_id) + ", ";
          String core = "\"coreid\": \"" + System.deviceID() + "\"";
          String blob = "{ " + data + version + product + core + " }";

          int r = udp.sendPacket(blob.c_str(),blob.length(), IPAddress(141,212,11,145), 5000);
          if(r < 0) {
            handle_error("Data publishing error", false);
            Serial.printlnf("Got error code: %d",r);
          } else {
            DataDeque.pop_front();
            last_cloud_event = millis();
          }

        } else {
          count = 0;
          state = nextState(state);
        }

        udp.stop();
      } else {
        handle_error("Data publishing error", false);
        count = 0;
        state = nextState(state);
      }

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
        count = 0;
        SD.PowerOff();
        state = nextState(state);
      }

      break;
    }

    case SendError: {
      manageStateTimer(30000);
      static int count = 0;

      if(Particle.connected()) {
        if(!CloudQueue.empty() && count < 4) {
          count++;
          Cloud::Publish("!",CloudQueue.front());
          CloudQueue.pop();
        } else {
          state = nextState(state);
          count = 0;
        }
      } else {
        state = nextState(state);
        count = 0;
      }

      break;
    }

    /*case CheckSMS: {
      manageStateTimer(120000);

      SINGLE_THREADED_BLOCK() {
        if(uCmd.checkMessages(10000) == RESP_OK) {
          uCmd.smsPtr = uCmd.smsResults;
          Serial.printlnf("Got %d messages",uCmd.numMessages);
          bool first = true;

          for(unsigned int i = 0; i < uCmd.numMessages; i++) {
            Serial.printlnf("Got message: %s from %s",uCmd.smsPtr->sms,uCmd.smsPtr->phone);
            String message = String(uCmd.smsPtr->sms);
            String phone = String(uCmd.smsPtr->phone);

            // Delete the message
            if(uCmd.deleteMessage(uCmd.smsPtr->mess,10000) == RESP_OK) {
              Serial.println("Deleted message");
            } else {
              Serial.println("Error deleting message");
            }

            if(first) {
              // Respond to the message
              first = false;
              if(message == "!Status") {
                Serial.println("About to send status response");
                String packet = stringifyResults(sensingResults);
                if(!uCmd.sendMessage((char*)packet.substring(0,100).c_str(), (char*)phone.c_str(), 10000) == RESP_OK) {
                  Serial.println("Error sending message");
                }
                if(!uCmd.sendMessage((char*)packet.substring(100,200).c_str(), (char*)phone.c_str(), 10000) == RESP_OK) {
                  Serial.println("Error sending message");
                }
                if(packet.length() > 200) {
                  if(!uCmd.sendMessage((char*)packet.substring(200).c_str(), (char*)phone.c_str(), 10000) == RESP_OK) {
                    Serial.println("Error sending message");
                  }
                }
              } else if(message == "!Reset") {
                Serial.println("About to send reset response");
                char stat[140] = "ACK";
                if(!uCmd.sendMessage(stat, (char*)phone.c_str(), 10000) == RESP_OK) {
                  Serial.println("Error sending message");
                }
                delay(10000);
                reset_helper();
              }
            }
          }

          state = nextState(state);
        } else {
          handle_error("SMS Check error", false);
          state = nextState(state);
        }
      }

      break;
    }*/

    case Wait: {
      manageStateTimer(1200000);

      static bool first = false;
      static unsigned long mill = 0;
      if(!first) {
        mill = millis();
        first = true;
      }

      if(millis() - mill > 60000) {
        clearResults(&sensingResults);
        system_cnt++;
        state = CheckCloudEvent;
        first = false;
      }
      break;
    }

    default: {
      state = CheckCloudEvent;
      lastState = Wait;
      break;
    }
  }

  //Call the automatic watchdog
  wd.checkin();

}

void soft_watchdog_reset() {
  //reset_flag = true; //let the reset subsystem shutdown gracefully
  //TODO change to system reset after a certain number of times called
  System.reset();
}

String id() {
  byte i;
  boolean present;
  byte data[8];     // container for the data from device
  char temp[4];
  String id = "";
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
    snprintf(temp, 4, "%02X", data[0]);
    id.concat(String(temp));
    Serial.print("Hex ROM data: ");
    for (i = 1; i <= 6; i++)
    {
      data[i] = ds.read(); //store each byte in different position in array
      snprintf(temp, 4, "%02X", data[i]);
      id.concat(String(temp));
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

    if(crc_calc == crc_byte) {
      return id;
    } else {
      return "ERR";
    }
  }
  else //Nothing is connected in the bus
  {
    Serial.println("xxxxx Nothing connected xxxxx");
    return "ERR";
  }
}


void PrintTwoDigitHex (byte b, boolean newline)
{
  Serial.print(b/16, HEX);
  Serial.print(b%16, HEX);
  if (newline) Serial.println();
}
