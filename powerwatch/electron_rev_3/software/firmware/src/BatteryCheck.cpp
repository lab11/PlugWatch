#include "BatteryCheck.h"

static FuelGauge fuel;
static PMIC pmic;

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

	// If the state of charge is good and less than minimum sleepT
	// We don't consider current charging state because of previous problems
	// Dying even while charging
	if (soc != 0.0 && soc < minimumSoC) {
		System.sleep(SLEEP_MODE_DEEP, sleepTimeSecs);
	}

}
