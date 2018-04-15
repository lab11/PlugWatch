#include <Particle.h>

#include "Cloud.h"
#include "Heartbeat.h"
#include "FileLog.h"

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
  res = res + String("|") + String(System.versionNumber());
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

void Heartbeat::send(bool force) {
    String meta = do_meta_data();
    String message = String(*count)+String("|")+String(meta);
    if (force) {
      message = "FORCE|" + message;
    }
    log.append(message);
    Cloud::Publish(HEARTBEAT_EVENT, message);
}

void Heartbeat::periodic(bool force) {
  (*count)++;
  log.append("Heartbeat! Count: " + String(*count));
  send(force);
}

int Heartbeat::cloudCommand(String command) {
  if ((command == "gc") || (command == "get count")) {
    return *count;
  }

  return super::cloudCommand(command);
}
