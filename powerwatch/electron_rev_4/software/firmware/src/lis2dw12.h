// Datasheet: http://www.st.com/content/ccc/resource/technical/document/datasheet/group3/30/3a/4e/6b/68/16/4a/35/DM00323179/files/DM00323179.pdf/jcr:content/translations/en.DM00323179.pdf
#pragma once

#define G                     9.8
#define LIS2DW12_CTRL1        0x20
#define LIS2DW12_CTRL2        0x21
#define LIS2DW12_CTRL3        0x22
#define LIS2DW12_CTRL4_INT1   0x23
#define LIS2DW12_CTRL5_INT2   0x24
#define LIS2DW12_CTRL6        0x25
#define LIS2DW12_CTRL7        0x3F
#define LIS2DW12_STATUS       0x27
#define LIS2DW12_OUT_X_L      0x28
#define LIS2DW12_FIFO_CTRL    0x2E
#define LIS2DW12_WAKE_UP_THS  0x34
#define LIS2DW12_WAKE_UP_DUR  0x35

#define LIS2DW12_I2C_ADDRESS   0x18

class lis2dw12 {
protected:
    enum  odr_t {
      odr_power_down = 0,
      odr_12_5_1_6,  // High power/Low power 12.5/1.6 Hz
      odr_12_5,      // High power/Low power 12.5 Hz
      odr_25,        // High power/Low power 25 Hz
      odr_50,        // High power/Low power 50 Hz
      odr_100,       // High power/Low power 100 Hz
      odr_200,       // High power/Low power 200 Hz
      odr_400,       // High power/Low power 400/200 Hz
      odr_800,       // High power/Low power 800/200 Hz
      odr_1600,      // High power/Low power 1600/200 Hz
    };

    enum mode_t {
      low_power = 0,
      high_performance,
      on_demand,
    };

    enum lp_mode_t {
      lp_1 = 0,
      lp_2,
      lp_3,
      lp_4,
    };

    enum bandwidth_t {
      bw_odr_2 = 0,
      bw_odr_4,
      bw_odr_10,
      bw_odr_20,
    };

    enum full_scale_t {
      fs_2g = 0,
      fs_4g,
      fs_8g,
      fs_16g,
    };

    enum fifo_mode_t {
      fifo_bypass= 0,
      fifo_stop = 1,
      fifo_cont_to_fifo = 3,
      fifo_byp_to_cont = 4,
      fifo_continuous = 6,
    };

    struct config_t {
      odr_t odr;
      mode_t mode;
      lp_mode_t lp_mode;
      bool cs_nopull;
      bool bdu;
      bool auto_increment;
      bool i2c_disable;
      bool sim;
      bool int_open_drain;
      bool int_latch;
      bool int_active_low;
      bool on_demand;
      bandwidth_t bandwidth;
      full_scale_t fs;
      bool high_pass;
      bool low_noise;
    };

    struct int_config_t {
      bool int1_6d;
      bool int1_sngl_tap;
      bool int1_wakeup;
      bool int1_free_fall;
      bool int1_dbl_tap;
      bool int1_fifo_full;
      bool int1_fifo_thresh;
      bool int1_data_ready;
      bool int2_sleep_state;
      bool int2_sleep_change;
      bool int2_boot;
      bool int2_temp_ready;
      bool int2_fifo_over;
      bool int2_fifo_full;
      bool int2_fifo_thresh;
      bool int2_data_ready;
    };

    struct fifo_config_t {
      fifo_mode_t mode;
      uint8_t thresh; // 0 - 32
    };

    struct wakeup_config_t {
      bool sleep_enable;
      uint8_t threshold;
      uint8_t wake_duration;
      uint8_t sleep_duration;
    };

    struct status_t {
      bool fifo_thresh;
      bool wakeup;
      bool sleep;
      bool dbl_tap;
      bool sngl_tap;
      bool _6D;
      bool free_fall;
      bool data_ready;
    };

public:
    lis2dw12() {};
    void  read_reg(uint8_t reg, uint8_t* read_buf, size_t len);
    void  write_reg(uint8_t reg, uint8_t* write_buf, size_t len);
    void  config(config_t config_s);
    void  interrupt_config(int_config_t config);
    void  interrupt_enable(bool enable);
    void  fifo_config(fifo_config_t config);
    void  config_for_wake_on_motion(uint8_t motion_threshold);
    void  read_full_fifo(int16_t* x, int16_t* y, int16_t* z);
    void  wakeup_config(wakeup_config_t wake_config);
    status_t read_status(void);
    void  reset();
    void  off();
    void  on();

    config_t ctl_config;
    uint8_t full_scale;
};
