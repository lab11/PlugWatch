//***********************************
//* SD Log Instance Variables / Functions
//***********************************

#pragma once

#include "SDCard.h"

class FileLog {
  SDCard &sd;
  String filename;

public:
	FileLog(SDCard &sd, String filename) : sd{sd}, filename{filename} {}

  void append(String str);
};