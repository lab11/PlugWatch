#pragma once

extern int debug_led_1;
extern bool power_state_change_flag;


#define CHARGE_STATE_EVENT "gc"
#define HEARTBEAT_EVENT "gh"
#define IMU_EVENT "gi"
#define IMU_ERROR_EVENT "!i"
#define LIGHT_EVENT "gl"
#define NRFWIT_EVENT "n"

#define WIFI_SCAN_EVENT "gw"
#define WIFI_ERROR_EVENT "!w"
#define AUDIO_EVENT "ga"
#define AUDIO_ERROR_EVENT "!a"
#define GPS_EVENT "gt"

#define SD_ERROR_EVENT "!s"
#define SD_READ_EVENT "gsa"
#define SD_REBOOT_EVENT "gsb"
#define SD_QUERY_EVENT "gsc"
#define SD_DELETE_EVENT "gsd"

const int TWELVE_HOURS = 1000 * 60 * 60 * 12;
const String CHARGE_STATE_BATTERY = "b";
const String CHARGE_STATE_WALL = "w";
