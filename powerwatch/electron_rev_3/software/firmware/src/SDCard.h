/**
 * SD Card related operations.
 */

#pragma once

#include <Particle.h>

#include <SdFat.h>

#include "Subsystem.h"

class SDCard: public Subsystem {
	typedef Subsystem super;

	const uint8_t SD_INT_PIN = D6; //TODO add in cloud event if this changes
	const uint8_t SD_ENABLE_PIN = D5;
	const uint8_t SD_CHIP_SELECT = A2;

	// SCK => A3, MISO => A4, MOSI => A5, SS => A2 (default)
	SdFat sd; // rev3 HW

	bool power_cycle_flag = false;
	bool removed_flag = false;
	String read_filename = "";
	String query_filename = "";
	String delete_filename = "";

public:
	explicit SDCard() :
		Subsystem(*this, "sd_log.txt") {}

	// Arduino setup
	void setup();

	// Arduino loop
	void loop();

  /**
	 * Power cycles SD Card. Blocking call.
	 */
	void PowerCycle();

	void Write(String filename, String to_write);

	String Stat(String filename);

  bool Delete(String filename);

	String Read(String filename);

private:
	String cloudFunctionName() { return "sd"; }
	bool sendDataOverTCP(String data);
	int cloudCommand(String command);
};
