#pragma once

#include <Particle.h>

#define ISL29035_ADDR 0x44
#define COMMAND_1     0
#define COMMAND_2     1
#define DATA_LSB      2
#define DATA_MSB      3
#define INT_LT_LSB    4
#define INT_LT_MSB    5
#define INT_HT_LSB    6
#define INT_HT_MSB    7
#define ID            8


class ISL29035 {
  protected:
    // Number of cycles interrupt persists for
    enum ISL29035_int_persist_cycles: int {
      CYCLES_1  = 0,
      CYCLES_4  = 1,
      CYCLES_8  = 2,
      CYCLES_16 = 3,
    };

    // Operational mode
    enum ISL29035_op_mode: int {
      SHUTDOWN        = 0,
      ALS_ONCE        = 1,
      IR_ONCE         = 2,
      ALS_CONTINUOUS  = 5,
      IR_CONTINUOUS    = 6,
    };

    // Sensing range (lux)
    enum ISL29035_range: int {
      LUX_1000  = 0,
      LUX_4000  = 1,
      LUX_16000 = 2,
      LUX_64000 = 3,
    };

    // ADC resolution
    enum ISL29035_adc_resolution: int {
      BIT_16  = 0, // 105ms for integration
      BIT_12  = 1, // 6.5ms
      BIT_8   = 2, // 0.41ms
      BIT_4   = 3, // 0.0256ms
    };

    // Default values
    uint8_t int_persist_cycles = CYCLES_1;
    uint8_t op_mode = ALS_CONTINUOUS;
    uint8_t range = LUX_4000;
    uint8_t adc_res = BIT_12;

  public:
    ISL29035(ISL29035_int_persist_cycles int_persist_cycles, ISL29035_op_mode op_mode, ISL29035_range range, ISL29035_adc_resolution adc_res) :
      int_persist_cycles { int_persist_cycles },
      op_mode { op_mode },
      range { range },
      adc_res { adc_res } {}

    ISL29035() {};
    int8_t init();
    float getLux();
    void off();
    void set_int_thresh(int high_lux, int low_lux);

  private:
    int range_to_lux();
    int res_to_adc_bits();
    //int res_to_time(ISL29035_adc_resolution res);
};
