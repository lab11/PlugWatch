#pragma once

#include <Particle.h>

#include "FileLog.h"
#include "SDCard.h"
#include "Subsystem.h"
#include "uCommand.h"

class SMS: public Subsystem {
  typedef Subsystem super;

  //TODO make cloud setable
  String alertNumber = "+4136588407";
  String userCode = "1234"; //Only act and respond if your 'code' is correct.
  String messageText = "default"; //TODO
  String phoneReturn = "default"; //TODO
  String moduleID = "Testing unit"; //TODO
  uCommand uCmd;

  int smsSent = 0;
  int smsLimit = 10;
  int smsAvailableFlag = 0;

public:
  void setup();
  LoopStatus loop();

  void smsRecvFlag(void* data, int index);

private:
  int sendSMS(const char* msg, const char* telNr);
  int processMessage(String messageText, String phoneReturn);
  void smsRecvCheck();
  void deleteSMSOnStart();
};
