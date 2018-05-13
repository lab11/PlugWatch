#include <Particle.h>

#include "CellStatus.h"

LoopStatus CellStatus::loop() {

  uint32_t freemem = System.freeMemory();
  CellularSignal sig = Cellular.RSSI();

  result = String(System.version().c_str());
  result = result + String(DLIM) + String(System.versionNumber());
  result = result + String(DLIM) + String(freemem);
  result = result + String(DLIM) + String(sig.rssi) + String(DLIM) + String(sig.qual);

  CellularBand band_avail;
  if (Cellular.getBandSelect(band_avail)) {
    result  = result + String(DLIM) + String(band_avail);
  }
  else {
    result = result + String("|No Bands Avail");
  }

  return FinishedSuccess;
}


String CellStatus::getResult() {
    return result;
}
