/**
 * SD Card related operations.
 */

 #pragma once

#include <Particle.h>
#include <SdFat.h>

class SDCard {
public:
	/**
	 * You must call this out of setup() to initialize pins.
	 */
	void setup();

  /**
	 * Power cycles SD Card. Blocking call.
	 */
	void PowerCycle();

	void Write(String filename, String to_write);

	String Read(String filename);
};
