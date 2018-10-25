#include <Particle.h>

#include "PowerCheck.h"

void PowerCheck::setup() {
	// This can't be part of the constructor because it's initialized too early.
	// Call this from setup() instead.

	// BATT_INT_PC13
	attachInterrupt(LOW_BAT_UC, &PowerCheck::interruptHandler, this, FALLING);

	// Enable charging
	pmic.begin();
	pmic.enableCharging();
	setChargeCurrent();
	lowerChargeVoltage();
}

bool PowerCheck::getHasPower() {
	// Bit 2 (mask 0x4) == PG_STAT. If non-zero, power is good
	// This means we're powered off USB or VIN, so we don't know for sure if there's a battery
	byte systemStatus = pmic.getSystemStatus();
	hasPower = ((systemStatus & 0x04) != 0);
	return hasPower;
}

bool PowerCheck::enableCharging() {
	return pmic.enableCharging();
}

/**
 * Returns true if the Electron has a battery.
 */
bool PowerCheck::getHasBattery() {
	if (millis() - lastChange < 100) {
		// When there is no battery, the charge status goes rapidly between fast charge and
		// charge done, about 30 times per second.

		// Normally this case means we have no battery, but return hasBattery instead to take
		// care of the case that the state changed because the battery just became charged
		// or the charger was plugged in or unplugged, etc.
		return hasBattery;
	}
	else {
		// It's been more than a 100 ms. since the charge status changed, assume that there is
		// a battery
		return true;
	}
}

int PowerCheck::getChargeCurrent() {
	return pmic.getChargeCurrent();
}

void PowerCheck::setChargeCurrent() {
	pmic.setInputCurrentLimit(900);
	pmic.setChargeCurrent(0,0,0,0,0,0);
}

void PowerCheck::lowerChargeVoltage() {
	pmic.setChargeVoltage(4040);
}

/**
 * Returns true if the Electron is currently charging (red light on)
 */
bool PowerCheck::getIsCharging() {
	if (getHasBattery()) {
		byte systemStatus = pmic.getSystemStatus();

		// Bit 5 CHRG_STAT[1] R
		// Bit 4 CHRG_STAT[0] R
		// 00 – Not Charging, 01 – Pre-charge (<VBATLOWV), 10 – Fast Charging, 11 – Charge Termination Done
		byte chrgStat = (systemStatus >> 4) & 0x3;

		// Return true if battery is charging if in pre-charge or fast charge mode
		return (chrgStat == 1 || chrgStat == 2);
	}
	else {
		// Does not have a battery, can't be charging.
		// Don't just return the charge status because it's rapidly switching
		// between charging and done when there is no battery.
		return false;
	}
}

void PowerCheck::interruptHandler() {
	bool p = hasPower;
	if(p != getHasPower()) {
		if(p == true) {
			// We had power and now don't
			lastUnplugMillis = millis();
			lastUnplugTime = Time.now();
		} else {
			// We didn't have power and now do
			lastPlugMillis = millis();
			lastPlugTime = Time.now();
		}
	}
	lastChange = millis();
}
