#include <SdFat.h>
#include <Particle.h>
#include "firmware.h"
#include "PowerCheck.h"
#include "AssetTracker.h"

PRODUCT_ID(7381);
PRODUCT_VERSION(2);


String RESET_LOG = "reset_log.txt";
String SYNC_LOG = "sync_log.txt";
String POWER_LOG = "power_log.txt";
String HEARTBEAT_LOG = "heartbeat_log.txt";
String GPS_LATLNG_LOG = "gps_log.txt";

SYSTEM_MODE(MANUAL);
STARTUP(System.enableFeature(FEATURE_RETAINED_MEMORY));
SYSTEM_THREAD(ENABLED);
STARTUP(System.enableFeature(FEATURE_RESET_INFO));

retained bool charge_state;


//Reset BTN
retained uint8_t num_manual_reboots = 0;
int reset_btn = A0;

uint8_t loop_cnt = 0;
AssetTracker t = AssetTracker();

//***********************************
//* PowerCheck
//***********************************
PowerCheck powerCheck;

//SdFatSoftSpi<A4, A5, A3> sd; //soft_miso, soft_mosi, soft_sck
SdFat sd;
const uint8_t chipSelect = A2;
File myFile;
const uint8_t SD_ENABLE_PIN = D5;
long lastPublish = 0;
int delayMillis = 10000;
long lastPublishDebug = 0;
int delayDebugMillis = 1000;

String reset_message = "";
retained String last_system_event_time = "";
retained String last_system_event_str = "";

const int WIFI_ENABLE_PIN = B1;
const int AUDIO_ENABLE_PIN = B4;
const int BLE_ENABLE_PIN = B5;
const int DEBUG_LED = C5;
CellularBand band_avail;
CellularSignal sig;

void system_reset_to_safemode() {
  num_manual_reboots++;
  reset_message = String(num_manual_reboots);
}

const int HARDWARE_WATCHDOG_TIMEOUT_MS = 1000 * 60;
ApplicationWatchdog wd(HARDWARE_WATCHDOG_TIMEOUT_MS, System.reset);



void setup() {

  Serial.begin(9600);

  pinMode(reset_btn, INPUT);
  attachInterrupt(reset_btn, system_reset_to_safemode, RISING, 3);

  pinMode(WIFI_ENABLE_PIN, OUTPUT);
  pinMode(AUDIO_ENABLE_PIN, OUTPUT);
  pinMode(BLE_ENABLE_PIN, OUTPUT);

  pinMode(DEBUG_LED, OUTPUT);

  t.begin();
  t.gpsOn();

  sd_write("setup", "setup");
  powerCheck.setup();

  kill_most_subsystems();
  System.on(all_events, handle_all_system_events);

  Particle.keepAlive(15);
  Particle.variable("l_se_time", last_system_event_time);
  Particle.variable("l_se_type", last_system_event_str);
  Particle.variable("is_charging", charge_state);
  Particle.variable("loop", loop_cnt);

}



void kill_most_subsystems() {
  digitalWrite(WIFI_ENABLE_PIN, LOW);
  digitalWrite(AUDIO_ENABLE_PIN, LOW);
  digitalWrite(BLE_ENABLE_PIN, LOW);
}

void handle_all_system_events(system_event_t event, int param) {
  String time_str = String(Time.format(Time.now(), TIME_FORMAT_ISO8601_FULL));
  String system_event_str = String((int)event) + "|" + String(param);
  last_system_event_time = time_str;
  last_system_event_str = system_event_str;
}


void sd_write(String filename, String to_write) {
  delay(200);
	if (!sd.begin(chipSelect, SPI_HALF_SPEED)) {
		Serial.println("CAN'T OPEN SD");
    digitalWrite(SD_ENABLE_PIN, HIGH);
  	delay(1000);
  	digitalWrite(SD_ENABLE_PIN, LOW);
  	delay(1000);
    Serial.println("DONE PowerCycle");
    return;
	}
	File file_to_write;
	String time_str = String(Time.format(Time.now(), TIME_FORMAT_ISO8601_FULL));
	String final_to_write = time_str + String("|") + String(to_write);
	if (!file_to_write.open(filename, O_WRITE | O_CREAT | O_AT_END)) {
		Serial.println(String("opening ") + String(filename) + String(" for write failed"));
		return;
	}
	file_to_write.println(final_to_write);
	file_to_write.close();
	Serial.println(String("wrote : ") + String(filename) + String(":") + to_write);
}

void loop() {
  t.updateGPS();

  if (System.updatesPending())
  {
    int ms = millis();
    while(millis()-ms < 60000) Particle.process();
  }

  if (reset_message != "") {
    reset_message = "";
    delay(200);
    Particle.publish("gr", reset_message);
    sd_write(RESET_LOG, reset_message);
    delay(100);
    System.enterSafeMode();
  }

  //******************************************
  // Sync if needed
  //******************************************
  if (! Particle.syncTimePending()) { // if not currently syncing
    unsigned long now = millis();
    unsigned long last = Particle.timeSyncedLast();
    if ((now - last) > TWELVE_HOURS) { // been a while
      Serial.println("syncing");
      sd_write(SYNC_LOG, "now " + String(now) + ", last sync " + String(last));
      Particle.syncTime(); // kick off a sync
    }
  }



  //******************************************
  // Allow particle to do any processing
  // https://docs.particle.io/reference/firmware/photon/#manual-mode
  //******************************************
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


  if (millis()-lastPublish > delayMillis) {
       digitalWrite(DEBUG_LED, HIGH);
       lastPublish = millis();
       Serial.println("sensing");
       //******************************************
       // Power Change
       //******************************************
       static bool last_charge_state = false;
       charge_state = powerCheck.getIsCharging();
       if (charge_state != last_charge_state) {
         Serial.println(String(charge_state));
         //power_send();
         last_charge_state = charge_state;
         sd_write(POWER_LOG, String(charge_state));
         String power_stats = String(FuelGauge().getSoC()) + String("|") + String(FuelGauge().getVCell()) + String("|") + String(powerCheck.getIsCharging());
         Particle.publish("gp", power_stats);
       }
      //*******************************************
      // GPS
      //*******************************************
      if (t.gpsFix()) {
          String latlng = t.readLatLon();
          sd_write(GPS_LATLNG_LOG, latlng);
          Particle.publish("gf", String(latlng));
          Serial.println(latlng);
      }


       //******************************************
       // Heartbeat Change
       //******************************************
       take_heartbeat();


       loop_cnt++;

       delay(500);
       digitalWrite(DEBUG_LED, LOW);
   }


   if (millis()-lastPublishDebug > delayDebugMillis) {
     lastPublishDebug = millis();
     Serial.println("checking");
     digitalWrite(DEBUG_LED, LOW);
     delay(50);
     digitalWrite(DEBUG_LED, HIGH);
   }


   wd.checkin();


}

void take_heartbeat() {
  uint32_t freemem = System.freeMemory();
  sig = Cellular.RSSI();
  String res = String(System.version().c_str());
  res = res + String("|") + String(System.versionNumber());
  res = res + String("|") + String(freemem);
  res = res + String("|") + String(sig.rssi) + String("|") + String(sig.qual);
  res = res + String("|") + String(charge_state);
  res = res + String("|") + String(loop_cnt);
  Serial.println("hb: " + String(res));
  sd_write(HEARTBEAT_LOG, res);
  Particle.publish("gh", String(res));
}
