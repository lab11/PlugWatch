//***********************************
//* SD Log Instance Variables / Functions
//***********************************

#pragma once

#include <queue>

#define MINOR_DLIM "|"
#define MAJOR_DLIM ";"

class SDCard;
//retained char current_name[50];

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

  //appends to the running log of name filename
  bool append(String str);

  //appends to a log of name year-month-day_filename
  bool appendAndRotate(String str, uint32_t unixTime);

  //these two functions allow us to easily make a dequeue out of an SD card file
  //gets the last line of a log
  String getLastLine();

  //removes last line of a log
  bool removeLastLine();

  // Print to serial, log to SD, publish to cloud
  void errorFromISR(String str);
  bool error(String str);

  // Get size
  int getFileSize();
  int getRotatedFileSize(uint32_t unitTime);

private:
  void processIsrQueue();
};
