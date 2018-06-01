#include "Timesync.h"

LoopStatus Timesync::loop() {
  if(Particle.connected()) {
    if (Particle.syncTimePending()) {
      return NotFinished;
    } else {
      // Do we need to sync?
      unsigned long now = millis();
      unsigned long last = Particle.timeSyncedLast();

      if ((now - last) > Timesync::TWELVE_HOURS) { // been a while
        Particle.syncTime(); // kick off a sync
        return NotFinished;
      } else {
        return FinishedSuccess;
      }
    }
  } else if (Cellular.ready()) {
    //Try to time sync with NTP
    Serial.println("Starting cellular sync");
    UDP udp;
    if(udp.begin(2390) != true) {
      Serial.println("UDP Begin error");
      return FinishedError;
    }
    uint8_t packet[48];
    packet[0] = 0x1B;
    unsigned long Sentmillis = millis();

    Serial.println("Sending packet");
    if(udp.sendPacket(packet, 48, IPAddress(128,138,141,172), 123) < 0) {
      Serial.println("Error sending udp packet");
      udp.stop();
      return FinishedError;
    }

    unsigned long mill = millis();
    while(millis() - mill < 40000) {
      int size = udp.receivePacket(packet, 48);
      if(size > 0) {

        Serial.printlnf("Received udp packet of size %d", size);
        udp.stop();

        unsigned long Receivedmillis = millis();
        if(packet[1] == 0) {
          Serial.println("Received kiss of death.");
          return FinishedError;
        }
        unsigned long NTPtime = packet[40] << 24 | packet[41] << 16 | packet[42] << 8 | packet[43];
        unsigned long NTPfrac = packet[44] << 24 | packet[45] << 16 | packet[46] << 8 | packet[47];

        if(NTPtime == 0) {
          return FinishedError;
        }

        unsigned long NTPmillis = (unsigned long)(((double)NTPfrac)  / 0xffffffff * 1000);
        NTPmillis += (Receivedmillis - Sentmillis)/2;
        if(NTPmillis >= 1000) {
          NTPmillis -= 1000;
          NTPtime += 1;
        }

        unsigned long t = NTPtime - 2208988800UL + 1;
        Serial.printlnf("Got time %lu", t);
        Time.setTime(t);
        return FinishedSuccess;
      }
    }

    udp.stop();
    return FinishedError;
  } else {
    return FinishedError;
  }
}
