#include <Particle.h>

#include "PowerCheck.h"

void PowerCheck::setup() {
	// This can't be part of the constructor because it's initialized too early.
	// Call this from setup() instead.

	// BATT_INT_PC13
	attachInterrupt(LOW_BAT_UC, &PowerCheck::interruptHandler, this, FALLING);

	//Drive the pin low
	pinMode(C3, OUTPUT);
	digitalWrite(C3, LOW);

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

int PowerCheck::getVoltage() {
    Serial.println("getting voltage");
		digitalWrite(C3, HIGH);
    int count = 0;
    int L_max = 0;
    int N_measure = 0;

    while(count < 10000) {
        int L = analogRead(B4);
        int N = analogRead(B2);
        if(L > L_max) {
            L_max = L;
            N_measure = N;
        }
        count++;
    }

    Serial.printlnf("L voltage count: %d", L_max);
    Serial.printlnf("N voltage count: %d", N_measure);

    float L_volt = ((L_max/4096.0 * 3.3) - (3.3/2))*(953/4.99);
    float N_volt = ((N_measure/4096.0 * 3.3) - (3.3/2))*(953/4.99);
		int volt = (int)(L_volt - N_volt);
		digitalWrite(C3, LOW);
		Serial.printlnf("Calculated voltage: %d",volt);
    return volt;
}

int PowerCheck::getLCycles() {
    //Do NOT do this if the system has power
    Serial.println("getting L cycles");
    if(this->getHasPower()) {
        return 0;
    }

    //set the perturb output to an output and low
    pinMode(A0, OUTPUT);
    digitalWrite(A0, LOW);
    setADCSampleTime(ADC_SampleTime_3Cycles);

    //let it go low
    delay(50);

    //we high and read in a loop counting cycles
    digitalWrite(A0, HIGH);
    /*int observe = analogRead(B5);
    int count = 0;
    while(observe < 1240 && count < 100000) {
        observe = analogRead(B5);
        //Serial.printlnf("Observe %d", observe);
        count++;
    }*/
		delay(10);
		int count = analogRead(B5);
    digitalWrite(A0, LOW);
    delay(10);
    pinMode(A0, INPUT);

    Serial.printlnf("Got %d cycles for L", count);

    if(count == 1000000) {
        return 0;
    } else {
        return count;
    }

}

int PowerCheck::getNCycles() {
    //Do NOT do this if the system has power
    Serial.println("getting N cycles");
    if(this->getHasPower()) {
        return 0;
    }

    //set the perturb output to an output and low
    pinMode(A1, OUTPUT);
    digitalWrite(A1, LOW);
    setADCSampleTime(ADC_SampleTime_3Cycles);

    //let it go low
    delay(50);

    //we high and read in a loop counting cycles
    digitalWrite(A1, HIGH);
    /*int observe = analogRead(B3);
    int count = 0;
    while(observe < 1240 && count < 100000) {
        observe = analogRead(B3);
        //Serial.printlnf("Observe %d", observe);
        count++;
    }*/
		delay(10);
		int count = analogRead(B3);

    digitalWrite(A1, LOW);
    delay(10);
    pinMode(A1, INPUT);

    Serial.printlnf("Got %d cycles for N", count);

    if(count == 1000000) {
        return 0;
    } else {
        return count;
    }
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
