#include <Particle.h>

#include "Cloud.h"
#include "Heartbeat.h"
#include "FileLog.h"

retained int Heartbeat::frequency = 1000 * 60 * 15;
retained int Heartbeat::count = 0;


void Heartbeat::timerCallback() {
  count++;
  timer_flag = true;
}

void Heartbeat::setup() {
  timer.start();
  Particle.function("hb", &Heartbeat::cloudCommand, this);

  log.append("Heartbeat setup complete. Initial frequency " + String(frequency));
}

//Meta Data
String do_meta_data() {
  String res;
  uint32_t freemem = System.freeMemory();
  CellularSignal sig = Cellular.RSSI();
  CellularBand band_avail;
  //String power_stats = String(FuelGauge().getSoC()) + String("|") + String(FuelGauge().getVCell()) + String("|") + String(powerCheck.getIsCharging());
  // TODO Fix PowerCheck
  String power_stats = String(FuelGauge().getSoC()) + String("|") + String(FuelGauge().getVCell());

  res = String(System.version().c_str());
  res = res + String("|") + power_stats;
  res = res + String("|") + String(freemem);
  res = res + String("|") + String(sig.rssi) + String("|") + String(sig.qual);

  if (Cellular.getBandSelect(band_avail)) {
    res  = res + String("|") + String(band_avail);
  }
  else {
    res = res + String("|No Bands Avail");
  }
  return res;
}

void Heartbeat::sendHeartbeat(bool force=false) {
    String meta = do_meta_data();
    String message = String(count)+String("|")+String(meta);
    if (force) {
      message = "FORCE|" + message;
    }
    log.append(message);
    Cloud::Publish(HEARTBEAT_EVENT, message);
}

void Heartbeat::loop() {
  if (timer_flag) {
    timer_flag = false;

    log.append("Heartbeat! Count: " + String(count));
    sendHeartbeat();
  }
  if (force_flag) {
    force_flag = false;

    log.append("FORCE Set flag");
    sendHeartbeat(true);
  }
}

int Heartbeat::setFrequencyFromISR(int new_frequency) {
  if ((new_frequency < MIN_FREQ) || (new_frequency > MAX_FREQ)) {
    log.appendFromISR("Error updating heartbeat frequency. Got: " + new_frequency);
    return -1;
  }

  frequency = new_frequency;
  timer.changePeriodFromISR(frequency);
  timer.resetFromISR();

  log.appendFromISR("Set heartbeat frequency to " + String(frequency));
  return 0;
}

int Heartbeat::cloudCommand(String command) {
  if (command == "enable") {
    timer.startFromISR();
    return 0;
  }
  if (command == "disable") {
    timer.stopFromISR();
    return 0;
  }
  if ((command == "now") || (command == "force")) {
    force_flag = true;
    return 0;
  }
  if ((command == "gc") || (command == "get count")) {
    return count;
  }
  if ((command == "gf") || (command == "get frequency")) {
    return frequency;
  }

  errno = 0;
  int new_frequency = strtol(command.c_str(), NULL, 10);
  if (errno != 0) {
    log.appendFromISR("Error updating heartbeat frequency. Got: " + frequency);
    return -1;
  }
  return setFrequencyFromISR(new_frequency);
}
