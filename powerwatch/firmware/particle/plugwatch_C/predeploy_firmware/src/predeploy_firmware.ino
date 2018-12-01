/*
 * Project BaseMetaData
 * Description:
 * Author:
 * Date:
 */
#include <OneWire.h>
OneWire ds(B0);
bool handshake_flag = false;
SYSTEM_THREAD(ENABLED);
#include <APNHelperRK.h>

PRODUCT_ID(7011); // 7008 is PowerWatch_A; 7009 is PowerWatch_B
PRODUCT_VERSION(1);

int last_millis;

const APNHelperAPN apns[6] = {
 {"8901260", "wireless.twilio.com"},
 {"8923301", "http://mtnplay.com.gh"},
 {"8991101", "airtelgprs.com"},
 {"8958021", "gprsweb.digitel.ve"},
 {"8958021", "internet.digitel.ve"},
 {"8923400", "9mobile"}
};
APNHelper apnHelper(apns, sizeof(apns)/sizeof(apns[0]));

const int WIFI_ENABLE_PIN = B1;
const int AUDIO_ENABLE_PIN = B4;
const int BLE_ENABLE_PIN = B5;
SYSTEM_MODE(MANUAL);
int reset_btn = A0;


int force_handshake(String cmd) {
  handshake_flag = true;
  return 0;
}

void setup() {
  Serial.begin(9600);

  apnHelper.setCredentials();

  pinMode(reset_btn, INPUT);
  attachInterrupt(reset_btn, reset_helper, RISING, 3);

  pinMode(WIFI_ENABLE_PIN, OUTPUT);
  pinMode(AUDIO_ENABLE_PIN, OUTPUT);
  pinMode(BLE_ENABLE_PIN, OUTPUT);
  kill_most_subsystems();

  Particle.function("handshake", force_handshake);
  Particle.function("reset_state", reset_state);
  Particle.function("ping", ping_func);

}

void reset_helper() {
  System.reset();
}

int reset_state(String cmd) {
  System.reset();
}

int ping_func(String cmd) {
  return 1;
}

void kill_most_subsystems() {
  digitalWrite(WIFI_ENABLE_PIN, LOW);
  digitalWrite(AUDIO_ENABLE_PIN, LOW);
  digitalWrite(BLE_ENABLE_PIN, LOW);
}

void loop() {
  static bool once = false;

  int particle_connect_time;
  if(!Particle.connected()) {
    // Don't attempt to connect too frequently as connection attempts hang MY_DEVICES
    static unsigned long last_connect_time = 0;
    const int connect_interval_sec = 60;
    unsigned long now = millis(); // unix time

    if ((last_connect_time == 0) || (now-last_connect_time > connect_interval_sec*1000)) {
      last_connect_time = now;
      Particle.connect();
    }
  }
  particle_connect_time = millis();

  if (Particle.connected()) {
    Particle.process();
    if(!once) {
      Particle.keepAlive(30);
      once = true;
    }
  }
  if (System.updatesPending()) {
    if(last_millis - millis() < 60000) {
        Particle.process();
      last_millis = millis();
    }
  }

  if (handshake_flag) {
    handshake_flag = false;
    Particle.publish("spark/device/session/end", "", PRIVATE);
  }
  id();
}


void id() {
  byte i;
  boolean present;
  byte data[8];     // container for the data from device
  byte crc_calc;    //calculated CRC
  byte crc_byte;    //actual CRC as sent by DS2401
  present = ds.reset();
  if (present == TRUE)
  {
    ds.write(0x33);  //Send Read data command
    data[0] = ds.read();
    String myID = System.deviceID();
    Serial.print(myID);
    Serial.print(",");
    for (i = 1; i <= 6; i++)
    {
      data[i] = ds.read(); //store each byte in different position in array
      PrintTwoDigitHex (data[i], 0);
    }
    crc_byte = ds.read(); //read CRC, this is the last byte
    crc_calc = OneWire::crc8(data, 7); //calculate CRC of the data
    if (crc_byte != crc_calc) {
      Serial.println("!");
    } else {
      Serial.print("\n");
    }
    delay(2000);
  }
}

void PrintTwoDigitHex (byte b, boolean newline)
{
  Serial.print(b/16, HEX);
  Serial.print(b%16, HEX);
  if (newline) Serial.println();
}
