#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <app_uart.h>
#include <ble_conn_params.h>
#include <ble_stack_handler_types.h>
#include <led.h>
#include <nordic_common.h>
#include <nrf.h>
#include <nrf_delay.h>
#include <nrf_error.h>
#include <nrf_sdm.h>
#include <softdevice_handler.h>

#include <simple_ble.h>

#include "nrf_drv_config.h"


/*******************************************************************************
 * Macros
 ******************************************************************************/

//#define DEBUG(...) printf(__VA_ARGS__)
#define DEBUG(...)

// https://stackoverflow.com/questions/5595593
#define min(x, y) ({                \
    typeof(x) _min1 = (x);          \
    typeof(y) _min2 = (y);          \
    (void) (&_min1 == &_min2);      \
    _min1 < _min2 ? _min1 : _min2; })
#define max(x, y) ({                \
    typeof(x) _max1 = (x);          \
    typeof(y) _max2 = (y);          \
    (void) (&_max1 == &_max2);      \
    _max1 > _max2 ? _max1 : _max2; })

/*******************************************************************************
 * Platform Configuration
 ******************************************************************************/

#define LED0 29
#define UART_TX_BUF_SIZE        256
#define UART_RX_BUF_SIZE        256


/*******************************************************************************
 * Function Prototypes
 ******************************************************************************/


/*******************************************************************************
 * Global State
 ******************************************************************************/

// Intervals for advertising and connections
simple_ble_config_t _ble_config = {
  .device_id         = DEVICE_ID_DEFAULT,
  .adv_name          = "gw-adv-fwd",
  .adv_interval      = MSEC_TO_UNITS(500, UNIT_0_625_MS),
  .min_conn_interval = MSEC_TO_UNITS(10, UNIT_1_25_MS),
  .max_conn_interval = MSEC_TO_UNITS(1250, UNIT_1_25_MS)
};


#define OORT_BASE_UUID {0x00, 0x00, 0x48, 0x43, 0x45, 0x54, 0x43, 0x49, \
                        0x47, 0x4f, 0x4c, 0x49, 0xe0, 0xfe, 0x00, 0x00}
#define BLE_UUID_OORT_SERVICE_SENSOR  0xfee0
#define BLE_UUID_OORT_CHAR_SENSOR     0xfee1
#define BLE_UUID_OORT_CHAR_CLOCK      0xfee3

#define BLE_UUID_OORT_SERVICE_INFO    0x180a
#define BLE_UUID_OORT_CHAR_SYSTEMID   0x2a23


#define MIN_CONNECTION_INTERVAL MSEC_TO_UNITS(10, UNIT_1_25_MS)
#define MAX_CONNECTION_INTERVAL MSEC_TO_UNITS(10, UNIT_1_25_MS)
#define SLAVE_LATENCY           0
#define SUPERVISION_TIMEOUT     MSEC_TO_UNITS(4000, UNIT_10_MS)


// Override. Don't need for serialization.
__attribute__ ((const))
void ble_address_set (void) {
  // nop
}

uint16_t _conn_handle = BLE_CONN_HANDLE_INVALID;
uint16_t _char_handle_sensor   = 0;
uint16_t _char_handle_systemid = 0;
uint16_t _char_handle_clock    = 0;


// Need to keep track of where we are in the state machine.
typedef enum {
  OORT_STATE_NONE,
  OORT_STATE_SETUP,
  OORT_STATE_SETUP_SEARCHING,            // Looking for meter advertisement
} oort_state_e;

oort_state_e _state = OORT_STATE_NONE;
// What to move to once the current operation has finished.
oort_state_e _next_state = OORT_STATE_NONE;


ble_uuid_t _oort_info_service_uuid = {
  .uuid = BLE_UUID_OORT_SERVICE_INFO,
  .type = BLE_UUID_TYPE_BLE,
};

ble_uuid_t _oort_sensor_service_uuid = {
  .uuid = BLE_UUID_OORT_SERVICE_SENSOR,
  .type = BLE_UUID_TYPE_VENDOR_BEGIN,
};

ble_uuid_t _oort_info_systemid_characteristic_uuid = {
  .uuid = BLE_UUID_OORT_CHAR_SYSTEMID,
  .type = BLE_UUID_TYPE_VENDOR_BEGIN,
};

/*******************************************************************************
 * BLE Code
 ******************************************************************************/

// Convert a u8 buffer like so:
// [0x01, 0x09, 0x99] -> 0.999
//
// Returns value in 10^-4 of whatever the value actually is. So, if
// the value is say 120.3, this will return 1203000.
static int convert_oort_to_p1milliunits (const uint8_t* oort) {

  // First byte is the decimal shift.
  uint8_t decimal_point_shift = oort[0];
  // Start by getting the original value in a u16.
  uint16_t hex_value = (((uint16_t) oort[1]) << 8) | ((uint16_t) oort[2]);

  // Now iterate through the 4 bit chunks to convert the number to decimal.
  unsigned multiplier = 1;
  unsigned out_value  = 0;
  unsigned shifter    = 0;
  for (unsigned i = 0; i < 4; i++) {
    out_value += (((hex_value >> (i * 4)) & 0xF) * multiplier);

    // Leverage the fact that we are already doing powers of 10 to generate
    // the value we need to multiply at the end to get into 0.1 milliunits.
    if (decimal_point_shift == i) {
      shifter = multiplier;
    }

    multiplier *= 10;
  }

  // Now take into account where the decimal place should be
  out_value *= shifter;
  return out_value;
}


// Power Metering Advertisement Data Format
//
// Byte 0: Status
//   Bit 0 - 1 if socket on
//   Bit 1 - 1 if count down on
//   Bit 2 - 1 if stanby alert
//   Bit 3 - 1 if overload alert
//   Bit 4 - In Used
// Byte 1: Power Factor
// Byte 2-3:
//   High two bits: Current decimal point value
//   Next two bits: Wattage decimal point value
//   Last twelve: Voltage
// Byte 4-5:
//   Current
// Byte 6-7:
//   Wattage

unsigned int get_multiplier(unsigned int decimal_value) {
    switch(decimal_value) {
        case 0: return 1;
        case 1: return 10;
        case 2: return 100;
        case 3: return 1000;
    }
    return 0;
}

void ble_evt_adv_report(ble_evt_t* p_ble_evt) {
  ble_gap_evt_adv_report_t adv = p_ble_evt->evt.gap_evt.params.adv_report;

  uint8_t* cur = adv.data;

  while ((cur - adv.data) < adv.dlen) {
    uint8_t len = *cur++ - 1;
    uint8_t type = *cur++;

    if (type == 0xFF) {
      // Manufacturer Data

      if (len != 8) {
        // WIT packets are 8 bytes of data
        return;
      }

      unsigned status = cur[0];
      unsigned power_factor = cur[1];
      unsigned current_decimal_point_value = (cur[2] & 0xC0) >> 6;
      unsigned wattage_decimal_point_value = (cur[2] & 0x30) >> 4;
      unsigned voltage = (((cur[2] & 0x0F) << 8) + cur[3])/10;
      unsigned current_value = (((cur[4] >> 4) & 0xF)*1000 +
                                (cur[4] & 0xF)*100 +
                                ((cur[5] >> 4) & 0xF)*10 +
                                (cur[5] & 0xF));
      unsigned wattage_value = (((cur[6] >> 4) & 0xF)*1000 +
                                (cur[6] & 0xF)*100 +
                                ((cur[7] >> 4) & 0xF)*10 +
                                (cur[7] & 0xF));

      //uint8_t current_array[3] = {current_decimal_point_value, cur[4], cur[5]};
      //uint8_t wattage_array[3] = {wattage_decimal_point_value, cur[6], cur[7]};
      //unsigned current = convert_oort_to_p1milliunits(current_array);
      //unsigned wattage = convert_oort_to_p1milliunits(wattage_array);

      //  We really need to do 10^*decimal_point_value
      //  - but I don't really want to do floating point math
      //  So this instead
      unsigned current_multiplier = get_multiplier(current_decimal_point_value);
      unsigned current = current_value*current_multiplier;

      unsigned wattage_multiplier = get_multiplier(wattage_decimal_point_value);
      unsigned wattage = wattage_value*wattage_multiplier;

      // Validate that the math checks out
      // n.b. there is ~238x headroom in 15A * 120V = 1800W (or 18000000) for 32-bit math
      unsigned check_wattage = (voltage * current * power_factor) / (100);
      unsigned difference = max(wattage, check_wattage) - min(wattage, check_wattage);
      float error;
      if (wattage > 0) {
        error = ((float) difference) / ((float) wattage);
      } else {
        if (check_wattage < 5) {
          error = 0;
        } else {
          error = 100;
        }
      }

      if (error < 0.05) {
        // Probably a winner!

        //printf("%02x\tpf: %u\t%8u %8u %8u\t| %u\n", status, power_factor, voltage, current, wattage, check_wattage);
        // suppress unused
        (void)status;

        unsigned j;
        printf("\r");
        for (j=0; j<adv.dlen; j++) {
          printf("%02x", adv.data[j]);
        }
        printf("\n");
      }
    } else if (type == 0x09) {
      // Complete Local Name

      //char name[len+1];
      //memcpy(name, cur, len);
      //name[len] = '\0';
      //printf("Name: %s\n", name);
      ;
    } else {
      //printf("type: %02x\n", type);
      ;
    }

    cur += len;
  }
}

/*******************************************************************************
 * MAIN
 ******************************************************************************/

void uart_error_handle (app_uart_evt_t * p_event) {
    if (p_event->evt_type == APP_UART_COMMUNICATION_ERROR) {
      while(true) {
        nrf_delay_ms(500);
        led_toggle(LED0);
        nrf_delay_ms(500);
        led_toggle(LED0);
        nrf_delay_ms(100);
        led_toggle(LED0);
        nrf_delay_ms(100);
        led_toggle(LED0);
        nrf_delay_ms(100);
        led_toggle(LED0);
        nrf_delay_ms(100);
        led_toggle(LED0);
        nrf_delay_ms(100);
        led_toggle(LED0);
        nrf_delay_ms(100);
        led_toggle(LED0);
      }
        APP_ERROR_HANDLER(p_event->data.error_communication);
    } else if (p_event->evt_type == APP_UART_FIFO_ERROR) {
      while(true) {
        nrf_delay_ms(500);
        led_toggle(LED0);
        nrf_delay_ms(500);
        led_toggle(LED0);
        nrf_delay_ms(100);
        led_toggle(LED0);
        nrf_delay_ms(100);
        led_toggle(LED0);
        nrf_delay_ms(100);
        led_toggle(LED0);
        nrf_delay_ms(100);
        led_toggle(LED0);
        nrf_delay_ms(100);
        led_toggle(LED0);
        nrf_delay_ms(100);
        led_toggle(LED0);
        nrf_delay_ms(100);
        led_toggle(LED0);
        nrf_delay_ms(100);
        led_toggle(LED0);
        nrf_delay_ms(100);
        led_toggle(LED0);
        nrf_delay_ms(100);
        led_toggle(LED0);
      }
        APP_ERROR_HANDLER(p_event->data.error_code);
    }
}

/*
void ble_error (uint32_t error_code) {
  printf("BLE ERROR: Code = 0x%x\n", (int) error_code);
}
*/

void ble_error(uint32_t error_code) {
  while(true) {
    nrf_delay_ms(500);
    led_toggle(LED0);
    nrf_delay_ms(500);
    led_toggle(LED0);
    nrf_delay_ms(100);
    led_toggle(LED0);
    nrf_delay_ms(100);
    led_toggle(LED0);
  }
}

int main (void) {
  uint32_t err_code;

  // Setup nrf LEDs
  led_init(LED0);
  led_on(LED0);

  // Setup nrf UART
  const app_uart_comm_params_t comm_params = {
    RX_PIN_NUMBER,
    TX_PIN_NUMBER,
    0,
    0,
    APP_UART_FLOW_CONTROL_DISABLED,
    false,
    UART_BAUDRATE_BAUDRATE_Baud115200
  };

  APP_UART_FIFO_INIT(&comm_params,
      UART_RX_BUF_SIZE,
      UART_TX_BUF_SIZE,
      uart_error_handle,
      APP_IRQ_PRIORITY_LOW,
      err_code);
  APP_ERROR_CHECK(err_code);

  printf("!!! Boot. UART Init !!!\n");

  // Setup simple BLE. This does most of the nordic setup.
  simple_ble_init(&_ble_config);

  // Scan for advertisements
  simple_ble_scan_start();

  while (true) {
    power_manage();
  }
}
