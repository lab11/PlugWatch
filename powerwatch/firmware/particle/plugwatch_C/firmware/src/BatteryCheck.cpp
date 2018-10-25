#include "BatteryCheck.h"
#include "PowerCheck.h"

static FuelGauge fuel;
static PMIC pmic;
extern PowerCheck powerCheck;

BatteryCheck::BatteryCheck(float minimumSoC, long sleepTimeSecs)
	: minimumSoC(minimumSoC), sleepTimeSecs(sleepTimeSecs) {


}
BatteryCheck::~BatteryCheck() {

}

void BatteryCheck::setup() {
	checkAndSleepIfNecessary();
}

void BatteryCheck::loop() {
	if (millis() - lastCheckMs >= CHECK_PERIOD_MS) {
		lastCheckMs = millis();
		checkAndSleepIfNecessary();
	}
}

void BatteryCheck::checkAndSleepIfNecessary() {
	float soc = fuel.getSoC();
	Serial.printlnf("SoC: %f", soc);
	if(soc != 0.0 && soc < minimumSoC && !powerCheck.getHasPower()) {
	//if(soc != 0.0 && soc < minimumSoC && !pmic.isPowerGood()) {
		System.sleep(SLEEP_MODE_DEEP, sleepTimeSecs);
	}
}
