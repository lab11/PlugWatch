#include <Particle.h>

#include "Heartbeat.h"

LoopStatus Heartbeat::loop() {
  (*count)++;
  return FinishedSuccess;
}

String do_meta_data() {
  /*
  String res;
  uint32_t freemem = System.freeMemory();
  CellularSignal sig = Cellular.RSSI();
  CellularBand band_avail;
  //String power_stats = String(FuelGauge().getSoC()) + String(DLIM) + String(FuelGauge().getVCell()) + String(DLIM) + String(powerCheck.getIsCharging());

  //const char *addrStr = "8.8.8.8";

  //boolean ok = CellularHelper.ping(addrStr);
  //Serial.printlnf("ping addr %s=%d", addrStr, ok);

  // TODO Fix PowerCheck
  String power_stats = String(FuelGauge().getSoC()) + String("|") + String(FuelGauge().getVCell());

  res = String(System.version().c_str());
  res = res + String(DLIM) + String(System.versionNumber());
  res = res + String(DLIM) + power_stats;
  res = res + String(DLIM) + String(freemem);
  res = res + String(DLIM) + String(sig.rssi) + String(DLIM) + String(sig.qual);

  if (Cellular.getBandSelect(band_avail)) {
    res  = res + String(DLIM) + String(band_avail);
  }
  else {
    res = res + String("|No Bands Avail");
  }
  return res;
  */
  return "<Heartbeat Metadata>";
}

String Heartbeat::getResult() {
    //log.append("Heartbeat! Count: " + String(*count));
    String meta = do_meta_data();
    String message = String(*count)+String(DLIM)+String(meta);
    return(message);
}
