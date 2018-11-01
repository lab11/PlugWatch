#include <AB1815.h>
#include <SPI.h>
#include "time.h"

void AB1815::init() {
    uint8_t start_reg = AB1815_CFG_KEY_REG | 0x80;

    //Start the SPI transaction
    SPI.begin(AB1815_CS);
    SPI.beginTransaction(__SPISettings(1*MHZ,MSBFIRST,SPI_MODE0));
    digitalWrite(AB1815_CS, LOW);
    SPI.transfer(start_reg);
    SPI.transfer(0x9D);
    SPI.transfer(0xA5);
    SPI.endTransaction();
    digitalWrite(AB1815_CS, HIGH);
    SPI.end();
}

void AB1815::setTime(uint32_t unixTime) {

    struct tm  * time;
    time = gmtime((time_t*)&unixTime);
    uint8_t tx[7];
    tx[0] = 0x00;

    tx[1] = ((time->tm_sec/10 << AB1815_SECOND_TENS_OFFSET) & AB1815_SECOND_TENS_MASK) |
            ((time->tm_sec % 10 << AB1815_SECOND_ONES_OFFSET) & AB1815_SECOND_ONES_MASK);

    tx[2] = ((time->tm_min/10 << AB1815_MINUTE_TENS_OFFSET) & AB1815_MINUTE_TENS_MASK) |
            ((time->tm_min % 10 << AB1815_MINUTE_ONES_OFFSET) & AB1815_MINUTE_ONES_MASK);

    tx[3] = ((time->tm_hour/10 << AB1815_HOUR_TENS_OFFSET) & AB1815_HOUR_TENS_MASK) |
            ((time->tm_hour % 10 << AB1815_HOUR_ONES_OFFSET) & AB1815_HOUR_ONES_MASK);

    tx[4] = ((time->tm_mday/10 << AB1815_DAY_TENS_OFFSET) & AB1815_DAY_TENS_MASK) |
            ((time->tm_mday % 10 << AB1815_DAY_ONES_OFFSET) & AB1815_DAY_ONES_MASK);

    tx[5] = (((time->tm_mon + 1)/10 << AB1815_MON_TENS_OFFSET) & AB1815_MON_TENS_MASK) |
            (((time->tm_mon +1) % 10 << AB1815_MON_ONES_OFFSET) & AB1815_MON_ONES_MASK);

    tx[6] = (((time->tm_year - 100)/10 << AB1815_YEAR_TENS_OFFSET) & AB1815_YEAR_TENS_MASK) |
            (((time->tm_year - 100) % 10 << AB1815_YEAR_ONES_OFFSET) & AB1815_YEAR_ONES_MASK);

    uint8_t start_reg = AB1815_TIME_DATE_REG | 0x80;

    //Start the SPI transaction
    SPI.begin(AB1815_CS);
    SPI.beginTransaction(__SPISettings(1*MHZ,MSBFIRST,SPI_MODE0));
    digitalWrite(AB1815_CS, LOW);
    SPI.transfer(start_reg);

    for(uint8_t i = 0; i < 7; i++) {
        SPI.transfer(tx[i]);
    }

    SPI.endTransaction();
    digitalWrite(AB1815_CS, HIGH);
    SPI.end();
    //digitalWrite(SCK, LOW);
    //digitalWrite(MISO, LOW);
    //digitalWrite(MOSI, LOW);
}

uint32_t AB1815::getTime(void) {
    uint8_t start_reg = AB1815_TIME_DATE_REG;

    //Start the SPI transaction
    SPI.begin(AB1815_CS);
    SPI.beginTransaction(__SPISettings(1*MHZ,MSBFIRST,SPI_MODE0));
    digitalWrite(AB1815_CS, LOW);
    SPI.transfer(start_reg);

    struct tm time;
    uint8_t rx[7];

    for(uint8_t i = 0; i < 7; i++) {
        rx[i] = SPI.transfer(0x00);
    }

    SPI.endTransaction();
    digitalWrite(AB1815_CS, HIGH);
    SPI.end();
    //digitalWrite(SCK, LOW);
    //digitalWrite(MISO, LOW);
    //digitalWrite(MOSI, LOW);

    time.tm_sec = ((rx[1] & AB1815_SECOND_TENS_MASK) >> AB1815_SECOND_TENS_OFFSET) * 10 +
                    ((rx[1] & AB1815_SECOND_ONES_MASK) >> AB1815_SECOND_ONES_OFFSET);

    time.tm_min = ((rx[2] & AB1815_MINUTE_TENS_MASK) >> AB1815_MINUTE_TENS_OFFSET) * 10 +
                    ((rx[2] & AB1815_MINUTE_ONES_MASK) >> AB1815_MINUTE_ONES_OFFSET);

    time.tm_hour = ((rx[3] & AB1815_HOUR_TENS_MASK) >> AB1815_HOUR_TENS_OFFSET) * 10 +
                    ((rx[3] & AB1815_HOUR_ONES_MASK) >> AB1815_HOUR_ONES_OFFSET);

    time.tm_mday = ((rx[4] & AB1815_DAY_TENS_MASK) >> AB1815_DAY_TENS_OFFSET) * 10 +
                    ((rx[4] & AB1815_DAY_ONES_MASK) >> AB1815_DAY_ONES_OFFSET);

    time.tm_mon = ((rx[5] & AB1815_MON_TENS_MASK) >> AB1815_MON_TENS_OFFSET) * 10 +
                    ((rx[5] & AB1815_MON_ONES_MASK) >> AB1815_MON_ONES_OFFSET) - 1;

    time.tm_year = ((rx[6] & AB1815_YEAR_TENS_MASK) >> AB1815_YEAR_TENS_OFFSET) * 10 +
                    ((rx[6] & AB1815_YEAR_ONES_MASK) >> AB1815_YEAR_ONES_OFFSET) + 100;

    return (uint32_t)mktime(&time);
}
