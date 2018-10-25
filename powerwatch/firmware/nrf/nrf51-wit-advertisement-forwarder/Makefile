PROJECT_NAME = $(shell basename "$(realpath ./)")

APPLICATION_SRCS = $(notdir $(wildcard ./*.c))
# Various C libraries that need to be included
APPLICATION_SRCS += app_error.c
APPLICATION_SRCS += app_fifo.c
APPLICATION_SRCS += app_timer.c
APPLICATION_SRCS += app_uart_fifo.c
APPLICATION_SRCS += app_util_platform.c
APPLICATION_SRCS += ble_advdata.c
APPLICATION_SRCS += ble_conn_params.c
APPLICATION_SRCS += ble_db_discovery.c
APPLICATION_SRCS += ble_srv_common.c
APPLICATION_SRCS += led.c
APPLICATION_SRCS += nrf_delay.c
APPLICATION_SRCS += nrf_drv_common.c
APPLICATION_SRCS += nrf_drv_uart.c
APPLICATION_SRCS += retarget.c
APPLICATION_SRCS += simple_adv.c
APPLICATION_SRCS += simple_ble.c
APPLICATION_SRCS += softdevice_handler.c
# Add other libraries here!

# platform-level headers and source files
LIBRARY_PATHS += ./
SOURCE_PATHS += ./

# Set the softdevice needed for the application
SOFTDEVICE_MODEL := s130
SDK_VERSION := 11

# Set up board information
BOARD := PowerWatchNrf
NRF_MODEL := nrf51
NRF_IC := nrf51822
RAM_KB := 32
FLASH_KB := 256

# Include the main Makefile
NRF_BASE_PATH ?= ../nrf5x-base
include $(NRF_BASE_PATH)/make/Makefile
