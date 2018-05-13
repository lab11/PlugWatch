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
#include "ESP8266.h"
#include "Wifi.h"
#include "Heartbeat.h"
#include "Imu.h"
#include "Light.h"
#include "NrfWit.h"
#include "SDCard.h"
#include "Subsystem.h"
#include "Timesync.h"
#include "firmware.h"
#include "OneWire.h"


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
PRODUCT_VERSION(2);
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
//* Heartbeat
//***********************************
retained int HEARTBEAT_COUNT = 0;
auto heartbeatSubsystem = Heartbeat(&HEARTBEAT_COUNT);

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
retained float LIGHT_LUX = 0;
auto lightSubsystem = Light(&LIGHT_LUX);

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

// TODO: Look into this library
//GoogleMapsDeviceLocator locator;


 //***********************************
 //* CLOUD FUNCTIONS
 //***********************************
 // Legacy functions
 int get_soc(String c) { //cloudfunction
     return (int)(FuelGauge().getSoC());
 }

 int get_battv(String c) { //cloudfunction
     return (int)(100 * FuelGauge().getVCell());
 }


//***********************************
//* System Events
//***********************************
// Not sure if we want to do anything with these in the long run, but for now
// just keep track of everything that happens
auto EventLog = FileLog(SD, "event_log.txt");

// String SYSTEM_EVENT = "s";
retained int system_event_count = 0;
retained String last_system_event_time = "";
retained int last_system_event_type = -999;
retained int num_reboots = 0;
retained int num_manual_reboots = 0;

void system_events_setup() {
  Particle.variable("d", system_event_count);
  Particle.variable("e", last_system_event_time);
  Particle.variable("f", last_system_event_type);
  Particle.variable("m", num_manual_reboots);
  Particle.variable("w", num_reboots);
}

void handle_all_system_events(system_event_t event, int param) {
  system_event_count++;
  Serial.printlnf("got event %d with value %d", event, param);
  String system_event_str = String((int)event) + "|" + String(param);
  String time_str = String(Time.format(Time.now(), TIME_FORMAT_ISO8601_FULL));
  last_system_event_time = time_str;
  last_system_event_type = param;
  Cloud::Publish(SYSTEM_EVENT, system_event_str);
  EventLog.append(system_event_str);
}

void system_reset_to_safemode() {
  num_manual_reboots++;
  Cloud::Publish(SYSTEM_EVENT, "manual reboot"); //TODO test if this hangs
  System.enterSafeMode();
}

int force_handshake(String cmd) {
  handshake_flag = true;
  return 0;
}

//***********************************
//* ye-old Arduino
//***********************************
void setup() {
  // if (System.resetReason() == RESET_REASON_PANIC) {
  //   System.enterSafeMode();
  // }

  // Some legacy bits that I'm not sure what we want to do with
  num_reboots++;
  Particle.variable("r", num_reboots);
  Particle.variable("v", String(System.version().c_str()));
  Particle.function("soc",get_soc);
  Particle.function("battv",get_battv);
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

  system_events_setup();

  timeSyncSubsystem.setup();
  heartbeatSubsystem.setup();
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

  Serial.println("Setup complete.");
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
  SenseTemp,
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

//Retained system states are used to diagnose restarts (error vs hard reset)
retained SystemState state = Wait;
retained SystemState lastState = Wait;

//State Timer is reused to make sure a state doesn't loop for too long.
//If it loops for too long we just call a reset. This can get us out of
//liveness bugs in a specific driver
Timer stateTimer(60000,soft_watchdog_reset,true);

//Before calling each state's loop function the state should call this
//function with a period of the maximum time the state is allowed to take
void manageStateTimer(unsigned int period) {
  if(state != lastState) {
          stateTimer.stop();
          stateTimer.changePeriod(period);
          stateTimer.reset();
          stateTimer.start();
          lastState = state;
  }
}

//This structure is what all of the drivers will return. It will
//be packetized and send to the cloud in the sendPacket state
struct ResultStruct {
    String chargeStateResult;
    String mpuResult;
    String wifiResult;
    String tempResult;
    String cellResult;
    String sdStatusResult;
    String lightResult;
    String witResult;
    String gpsResult;
};

ResultStruct sensingResults;

void loop() {

  if (System.updatesPending())
  {
    int ms = millis();
    while(millis()-ms < 60000) Particle.process();
  }

  if (handshake_flag) {
  handshake_flag = false;
  Particle.publish("spark/device/session/end", "", PRIVATE);
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

  static bool once = false;
  if (!once && Particle.connected()) {
         Particle.keepAlive(30); // send a ping every 30 seconds
         once = true;
  }


  switch(state) {
  case CheckCloudEvent: {

  }
  break;
  case CheckTimeSync: {
    manageStateTimer(180000);

    LoopStatus result = timeSyncSubsystem.loop();

    //return result or error
    if(result == FinishedError) {
      //Log the error in the error struct
    } else if(result == FinishedSuccess) {
      //get the result from the charge state and put it into the system struct
      state = SenseChargeState;
    }
  }
  break;
  case SenseChargeState: {
    //It should not take more than 1s to sense charge state
    manageStateTimer(1000);

    //Call the loop function for sensing charge state
    LoopStatus result = chargeStateSubsystem.loop();

    //return result or error
    if(result == FinishedError) {
      //Log the error in the error struct
    } else if(result == FinishedSuccess) {
      //get the result from the charge state and put it into the system struct
      sensingResults.chargeStateResult = chargeStateSubsystem.getResult();
      state = SenseMPU;
    }
  }
  break;
  case SenseMPU: {
    //It should not take more than 1s to check the IMU
    manageStateTimer(1000);

    LoopStatus result = imuSubsystem.loop();

    //return result or error
    if(result == FinishedError) {
      //Log the error in the error struct
    } else if(result == FinishedSuccess) {
      //get the result from the charge state and put it into the system struct
      sensingResults.mpuResult = imuSubsystem.getResult();
      state = SenseWiFi;
    }
  }
  break;
  case SenseWiFi: {
    //It might take a while to get all of the wifi SSIDs
    manageStateTimer(20000);

    LoopStatus result = wifiSubsystem.loop();

    //return result or error
    if(result == FinishedError) {
      //Log the error in the error struct
    } else if(result == FinishedSuccess) {
      //get the result from the charge state and put it into the system struct
      sensingResults.wifiResult = wifiSubsystem.getResult();
      state = SenseTemp;
    }
  }
  break;
  case SenseTemp: {
    //temperature should not take more than 1s
    manageStateTimer(1000);

    LoopStatus result = imuSubsystem.loop();

    //return result or error
    if(result == FinishedError) {
      //Log the error in the error struct
    } else if(result == FinishedSuccess) {
      //get the result from the charge state and put it into the system struct
      sensingResults.tempResult = imuSubsystem.getResult();
      state = SenseCell;
    }
  }
  break;
  case SenseCell: {
  }
  break;
  case SenseSDPresent: {
    //This should just be a GPIO pin
    manageStateTimer(1000);

    LoopStatus result = SD.loop();

    //return result or error
    if(result == FinishedError) {
      //Log the error in the error struct
    } else if(result == FinishedSuccess) {
      //get the result from the charge state and put it into the system struct
      sensingResults.sdStatusResult = SD.getResult();
      state = SenseLight;
    }
  }
  break;
  case SenseLight: {
    manageStateTimer(1000);

    LoopStatus result = lightSubsystem.loop();

    //return result or error
    if(result == FinishedError) {
      //Log the error in the error struct
    } else if(result == FinishedSuccess) {
      //get the result from the charge state and put it into the system struct
      sensingResults.lightResult = lightSubsystem.getResult();
      state = SenseWit;
    }
  }
  break;
  case SenseWit: {
    //This requires scanning for an advertisement
    manageStateTimer(5000);

    LoopStatus result = nrfWitSubsystem.loop();

    //return result or error
    if(result == FinishedError) {
      //Log the error in the error struct
    } else if(result == FinishedSuccess) {
      //get the result from the charge state and put it into the system struct
      sensingResults.witResult = nrfWitSubsystem.getResult();
      state = SenseGPS;
    }
  }
  break;
  case SenseGPS: {
    manageStateTimer(1000);
    LoopStatus result = gpsSubsystem.loop();

    //return result or error
    if(result == FinishedError) {
      //Log the error in the error struct
    } else if(result == FinishedSuccess) {
      //get the result from the charge state and put it into the system struct
      sensingResults.gpsResult = gpsSubsystem.getResult();
      state = SenseGPS;
    }
  }
  break;
  case LogPacket:
  break;
  case SendPacket:
  break;
  case LogError:
  break;
  case SendError:
  break;
  case Wait:
  break;
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
