#include <Particle.h>

#include <Wire.h>
#include "lis2dh12.h"


void lis2dh12::read_reg(uint8_t reg, uint8_t* read_buf, size_t len){
  if (len > 256) return;
  uint8_t readreg = reg | 0x80;

  if(!Wire.isEnabled()) {
    Wire.begin();
  }

  Wire.beginTransmission(LIS2DH12_I2C_ADDRESS);
  Wire.write(readreg);
  Wire.endTransmission(false);
  Wire.requestFrom(LIS2DH12_I2C_ADDRESS, len, true);
  for(size_t i = 0; i < len; i++) {
    read_buf[i] = Wire.read();
  }
}

void lis2dh12::write_reg(uint8_t reg, uint8_t* write_buf, size_t len){
  if (len > 256) return;

  Wire.beginTransmission(LIS2DH12_I2C_ADDRESS);
  Wire.write(reg);
  for(size_t i = 0; i < len; i++) {
    Wire.write(write_buf[i]);
  }
  Wire.endTransmission(true);
}


int8_t  lis2dh12::get_temp() {
    uint8_t temp;
    uint8_t dummy;
    read_reg(LIS2DH12_TEMP_OUTH, &temp, 1);
    read_reg(LIS2DH12_TEMP_OUTL, &dummy, 1);
    return (int8_t)temp;
}

void  lis2dh12::config_for_wake_on_motion(uint8_t motion_threshold) {
  // Turn on the accelerometer to 10Hz sampling
  on(2, 1);

  //Enable Int1 to tie to the IA interrupt
  uint8_t int_enable = 1 << 6;
  write_reg(LIS2DH12_CTRL3, &int_enable, 1);

  //Enable high pass filtering - essentially take the delta of motion
  uint8_t filter = 1 << 4 | 1;
  write_reg(LIS2DH12_CTRL2, &filter, 1);

  //set INT1 to latch on interrupt
  uint8_t latch = 1 << 3;
  write_reg(LIS2DH12_CTRL5, &latch, 1);

  //configure the INT1 SRC
  uint8_t src = 1 << 5 | 1 << 3 | 1 << 1;
  write_reg(LIS2DH12_INT1_CFG, &src, 1);

  //configure the motion threshold
  write_reg(LIS2DH12_INT1_THS, &motion_threshold, 1);

  //configure the INT1 durection
  uint8_t duration = 10;
  write_reg(LIS2DH12_INT1_THS, &duration, 1);
}

void lis2dh12::off() {
  uint8_t off_byte = 0 << 4 | (1) << 3 | 7;
  write_reg(LIS2DH12_CTRL1, &off_byte, 1);

  uint8_t off_temp = 0;
  write_reg(LIS2DH12_TEMP_CFG, &off_temp, 1);
}

void lis2dh12::on(uint8_t data_rate, uint8_t low_power_mode) {
  uint8_t on_byte = (data_rate << 4) | (low_power_mode & 0x01) << 3 | 7;
  write_reg(LIS2DH12_CTRL1, &on_byte, 1);

  uint8_t on_temp = 0xC0;
  write_reg(LIS2DH12_TEMP_CFG, &on_temp, 1);

  uint8_t bdu = 0x80;
  write_reg(LIS2DH12_CTRL4, &bdu, 1);
}

uint8_t lis2dh12::read_status(void) {
  uint8_t status_byte;
  uint8_t int1_src;
  read_reg(LIS2DH12_STATUS, &status_byte, 1);
  read_reg(LIS2DH12_INT1_SRC, &int1_src, 1);
  return status_byte;
}
