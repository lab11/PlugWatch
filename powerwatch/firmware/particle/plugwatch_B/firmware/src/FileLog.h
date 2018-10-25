//***********************************
//* SD Log Instance Variables / Functions
//***********************************

#pragma once

#include <queue>

class SDCard;

class FileLog {
  SDCard &sd;
  String filename;

  std::queue<String> isr_queue;

public:
	FileLog(SDCard &sd, String filename) : sd{sd}, filename{filename} {}

  // Only print to Serial
  void debugFromISR(String str);
  void debug(String str);

  // Print to serial and log to SD
  void appendFromISR(String str);
  void append(String str);

  // Print to serial, log to SD, publish to cloud
  void errorFromISR(String str);
  void error(String str);

private:
  void processIsrQueue();
};
