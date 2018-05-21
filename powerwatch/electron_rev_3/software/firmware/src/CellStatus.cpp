#include <Particle.h>

#include "CellStatus.h"

LoopStatus CellStatus::loop() {

  uint32_t freemem = System.freeMemory();
  CellularDevice dev;
  dev.size = sizeof(dev);
  cellular_device_info(&dev, NULL);
  if(Cellular.ready()) {
    CellularSignal sig = Cellular.RSSI();

    result = String(System.version().c_str());
    result = result + String(MINOR_DLIM) + String(System.versionNumber());
    result = result + String(MINOR_DLIM) + dev.imei + String(MINOR_DLIM) + dev.iccid;
    result = result + String(MINOR_DLIM) + String(freemem);
    result = result + String(MINOR_DLIM) + String(sig.rssi) + String(MINOR_DLIM) + String(sig.qual);

    CellularBand band_avail;
    if (Cellular.getBandSelect(band_avail)) {
      result  = result + String(MINOR_DLIM) + String(band_avail);
    }
    else {
      result = result + String(MINOR_DLIM) + String("No Bands Avail");
    }
  } else {
    result = String(System.version().c_str());
    result = result + String(MINOR_DLIM) + String(System.versionNumber());
    result = result + String(MINOR_DLIM) + dev.imei + String(MINOR_DLIM) + dev.iccid;
    result = result + String(MINOR_DLIM) + String(freemem);
    result = result + String(MINOR_DLIM) + "!" + String(MINOR_DLIM) + "!";
  }

  return FinishedSuccess;
}


String CellStatus::getResult() {
    return result;
}
