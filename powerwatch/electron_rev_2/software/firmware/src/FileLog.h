//***********************************
//* SD Log Instance Variables / Functions
//***********************************

#pragma once

class SDCard;

class FileLog {
  SDCard &sd;
  String filename;

public:
	FileLog(SDCard &sd, String filename) : sd{sd}, filename{filename} {}

  void appendFromISR(String str);
  void append(String str);
  void errorFromISR(String str);
  void error(String str);
  void debugFromISR(String str);
  void debug(String str);
};
