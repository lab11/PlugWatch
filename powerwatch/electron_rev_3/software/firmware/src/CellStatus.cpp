#include <Particle.h>

#include "CellStatus.h"

LoopStatus CellStatus::loop() {

  uint32_t freemem = System.freeMemory();
  CellularSignal sig = Cellular.RSSI();

  result = String(System.version().c_str());
  result = result + String(MINOR_DLIM) + String(System.versionNumber());
  result = result + String(MINOR_DLIM) + String(freemem);
  result = result + String(MINOR_DLIM) + String(sig.rssi) + String(MINOR_DLIM) + String(sig.qual);

  CellularBand band_avail;
  if (Cellular.getBandSelect(band_avail)) {
    result  = result + String(MINOR_DLIM) + String(band_avail);
  }
  else {
    result = result + String(MINOR_DLIM) + String("No Bands Avail");
  }

  return FinishedSuccess;
}


String CellStatus::getResult() {
    return result;
}
