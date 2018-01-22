/**
 * SD Card related operations.
 */

#pragma once

#include <Particle.h>

#include <SdFat.h>

#include "Subsystem.h"

class SDCard: public Subsystem {
	typedef Subsystem super;

	bool power_cycle_flag = false;
	String read_filename = "";

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

	String Read(String filename);

private:
	String cloudFunctionName() { return "sd"; }
	int cloudCommand(String command);
};
