#include <Particle.h>

#include <Wire.h>
#include "lis2dw12.h"


void lis2dw12::read_reg(uint8_t reg, uint8_t* read_buf, size_t len){
  if (len > 256) return;
  uint8_t readreg = reg | 0x80;

  if(!Wire.isEnabled()) {
    Wire.begin();
  }

  Wire.beginTransmission(LIS2DW12_I2C_ADDRESS);
  Wire.write(reg);
  Wire.endTransmission(false);
  Wire.requestFrom(LIS2DW12_I2C_ADDRESS, len, true);
  for(size_t i = 0; i < len; i++) {
    read_buf[i] = Wire.read();
  }
}

void lis2dw12::write_reg(uint8_t reg, uint8_t* write_buf, size_t len){
  if (len > 256) return;

  Wire.beginTransmission(LIS2DW12_I2C_ADDRESS);
  Wire.write(reg);
  for(size_t i = 0; i < len; i++) {
    Wire.write(write_buf[i]);
  }
  Wire.endTransmission(true);
}


void  lis2dw12::config_for_wake_on_motion(uint8_t motion_threshold) {
  wakeup_config_t wake_config = {
    .sleep_enable = 1,
    .threshold = motion_threshold,
    .wake_duration = 3,
    .sleep_duration = 2
  };

  config_t config_s = {
    .odr = odr_200,
    .mode = low_power,
    .lp_mode = lp_1,
    .cs_nopull = 0,
    .bdu = 1,
    .auto_increment = 1,
    .i2c_disable = 1,
    .int_open_drain = 0,
    .int_latch = 1,
    .int_active_low = 0,
    .on_demand = 1,
    .bandwidth = bw_odr_2,
    .fs = fs_4g,
    .high_pass = 0,
    .low_noise = 1,
  };

  int_config_t int_config = {0};
  int_config.int1_wakeup = 1;

  reset();
  config(config_s);
  interrupt_config(int_config);
  interrupt_enable(1);
  wakeup_config(wake_config);
}

void lis2dw12::config(config_t config) {
  ctl_config = config;

  switch (config.fs) {
    case fs_2g:
      full_scale = 2;
      break;
    case fs_4g:
      full_scale = 4;
      break;
    case fs_8g:
      full_scale = 8;
      break;
    case fs_16g:
      full_scale = 16;
      break;
  }

  uint8_t buf[6] = {0};
  buf[0] = config.odr << 4 | (config.mode & 0x3) << 2 |
           (config.lp_mode & 0x3);
  buf[1] = config.cs_nopull << 4 | config.bdu << 3 |
           config.auto_increment << 2 | config.i2c_disable << 1 |
           config.sim;
  buf[2] = config.int_open_drain << 5 | config.int_latch << 4 |
           config.int_active_low << 3 | config.on_demand << 1;
  buf[5] = config.bandwidth << 6 | config.fs << 4 |
           config.high_pass << 3 | config.low_noise << 2;

  write_reg(LIS2DW12_CTRL1, buf, 6);
}

void lis2dw12::interrupt_config(int_config_t config){

  uint8_t buf[2] = {0};
  buf[0]  = config.int1_6d << 7 | config.int1_sngl_tap << 6 |
                config.int1_wakeup << 5 | config.int1_free_fall << 4 |
                config.int1_dbl_tap << 3 | config.int1_fifo_full << 2 |
                config.int1_fifo_thresh << 1 | config.int1_data_ready;
  buf[1]  = config.int2_sleep_state << 7 | config.int2_sleep_change << 6 |
                config.int2_boot << 5 | config.int2_data_ready << 4 |
                config.int2_fifo_over << 3 | config.int2_fifo_full << 2 |
                config.int2_fifo_thresh << 1 | config.int2_data_ready;

  write_reg(LIS2DW12_CTRL4_INT1, buf, 2);
}
void lis2dw12::interrupt_enable(bool enable){
  uint8_t int_enable = enable << 5;
  write_reg(LIS2DW12_CTRL7, &int_enable, 1);
}

void lis2dw12::fifo_config(fifo_config_t config) {
  uint8_t fifo_byte = config.mode << 5 | (config.thresh & 0x1f);

  write_reg(LIS2DW12_FIFO_CTRL, &fifo_byte, 1);
}

void lis2dw12::read_full_fifo(int16_t* x, int16_t* y, int16_t* z) {
  uint8_t addr = 0x80 | LIS2DW12_OUT_X_L;

  Wire.beginTransmission(LIS2DW12_I2C_ADDRESS);
  Wire.write(addr);
  Wire.endTransmission(false);
  Wire.requestFrom(LIS2DW12_I2C_ADDRESS, 1+32*3*2, true);

  size_t i, xyz_i = 0;
  for (i = 1; i < 1 + 32*3*2; i += 6) {
    uint8_t one = Wire.read();
    uint8_t two = Wire.read();
    x[xyz_i] = (one  | (int16_t)two << 8);

    one = Wire.read();
    two = Wire.read();
    y[xyz_i] = (one | (int16_t)two << 8);

    one = Wire.read();
    two = Wire.read();
    z[xyz_i] = (one | (int16_t)two << 8);
    xyz_i++;
  }
}

void lis2dw12::reset() {
  uint8_t reset_byte = 1 << 6;

  write_reg(LIS2DW12_CTRL2, &reset_byte, 1);

  reset_byte = 0;
  while(reset_byte == 0) {
    read_reg(LIS2DW12_CTRL2, &reset_byte, 1);
  }
}

void lis2dw12::off() {
  odr_t odr_power_down = odr_power_down;
  uint8_t off_byte = odr_power_down << 4 | (ctl_config.mode & 0x3) << 2 |
               (ctl_config.lp_mode & 0x3);

  write_reg(LIS2DW12_CTRL1, &off_byte, 1);
}

void lis2dw12::on() {
  uint8_t on_byte = ctl_config.odr << 4 | (ctl_config.mode & 0x3) << 2 |
           (ctl_config.lp_mode & 0x3);

  write_reg(LIS2DW12_CTRL1, &on_byte, 1);
}

void lis2dw12::wakeup_config(wakeup_config_t wake_config) {
  uint8_t wake_ths_byte = wake_config.sleep_enable << 6 | (wake_config.threshold & 0x3f);
  uint8_t wake_dur_byte = (wake_config.wake_duration & 0x3) << 5 | (wake_config.sleep_duration & 0xf);

  write_reg(LIS2DW12_WAKE_UP_THS, &wake_ths_byte, 1);
  write_reg(LIS2DW12_WAKE_UP_DUR, &wake_dur_byte, 1);
}

lis2dw12::status_t lis2dw12::read_status(void) {
  uint8_t status_byte;
  read_reg(LIS2DW12_STATUS, &status_byte, 1);

  status_t status;

  status.fifo_thresh  = (status_byte >> 7) & 0x1;
  status.wakeup       = (status_byte >> 6) & 0x1;
  status.sleep        = (status_byte >> 5) & 0x1;
  status.dbl_tap      = (status_byte >> 4) & 0x1;
  status.sngl_tap     = (status_byte >> 3) & 0x1;
  status._6D          = (status_byte >> 2) & 0x1;
  status.free_fall    = (status_byte >> 1) & 0x1;
  status.data_ready   =  status_byte & 0x1;

  return status;
}
