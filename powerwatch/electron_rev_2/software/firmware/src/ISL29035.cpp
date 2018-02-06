#include <Wire.h>
#include <ISL29035.h>

int8_t ISL29035::init() {
  int8_t ret = 0;

  if (!Wire.isEnabled()) {
    Wire.begin();
  }

  uint8_t cmd1 = int_persist_cycles | op_mode << 5;
  uint8_t cmd2 = range | adc_res << 2;

  Wire.beginTransmission(ISL29035_ADDR);
  Wire.write(COMMAND_1);
  Wire.write(cmd1);
  Wire.write(cmd2);
  ret = Wire.endTransmission(true);
  return ret;
}

float ISL29035::getLux() {
  int8_t ret = 0;
  uint8_t data = 0;
  uint16_t lux_int = 0;

  Wire.beginTransmission(ISL29035_ADDR);
  Wire.write(DATA_LSB);
  ret = Wire.endTransmission(false);
  if (ret != 0) return (float)-ret;
  Wire.requestFrom(ISL29035_ADDR, 2, true);
  data = Wire.read();
  lux_int = data;
  data = Wire.read();
  lux_int |= data << 8;

  return (float) lux_int * (float) range_to_lux() / (float) res_to_adc_bits();
}

int ISL29035::range_to_lux() {
  switch(range) {
    case LUX_1000: return 1000;
    case LUX_4000: return 4000;
    case LUX_16000: return 16000;
    case LUX_64000: return 64000;
    default: return -1;
  }
}

int ISL29035::res_to_adc_bits() {
  switch(adc_res) {
    case BIT_16: return 1 << 16;
    case BIT_12: return 1 << 12;
    case BIT_8: return 1 << 8;
    case BIT_4: return 1 << 4;
    default: return -1;
  }
}
