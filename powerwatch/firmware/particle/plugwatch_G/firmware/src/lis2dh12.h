// Datasheet: http://www.st.com/content/ccc/resource/technical/document/datasheet/group3/30/3a/4e/6b/68/16/4a/35/DM00323179/files/DM00323179.pdf/jcr:content/translations/en.DM00323179.pdf
#pragma once

#define G                     9.8
#define LIS2DH12_CTRL1        0x20
#define LIS2DH12_CTRL2        0x21
#define LIS2DH12_CTRL3        0x22
#define LIS2DH12_CTRL4        0x23
#define LIS2DH12_CTRL5        0x24
#define LIS2DH12_STATUS       0x27
#define LIS2DH12_INT1_CFG     0x30
#define LIS2DH12_INT1_THS     0x32
#define LIS2DH12_INT1_SRC     0x31
#define LIS2DH12_TEMP_CFG     0x1F
#define LIS2DH12_TEMP_OUTL     0x0C
#define LIS2DH12_TEMP_OUTH     0x0D

#define LIS2DH12_I2C_ADDRESS   0x18

class lis2dh12 {
public:
    lis2dh12() {};
    void  read_reg(uint8_t reg, uint8_t* read_buf, size_t len);
    void  write_reg(uint8_t reg, uint8_t* write_buf, size_t len);
    uint8_t read_status(void);
    void  config_for_wake_on_motion(uint8_t motion_threshold);
    void  off();
    void  on(uint8_t data_rate, uint8_t power_mode);
    int8_t get_temp();
};
