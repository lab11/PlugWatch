#pragma once

#include <Particle.h>

#include "FileLog.h"
#include "SDCard.h"
#include "Subsystem.h"
#include "uCommand.h"

class SMS: public PeriodicSubsystem {
  typedef PeriodicSubsystem super;

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
  static const int DEFAULT_FREQ = 1000 * 60 * 1;  // 1 min

  void setup();
  void smsRecvFlag(void* data, int index);

  SMS(SDCard &sd, int* frequency) :
    PeriodicSubsystem(sd, "sms_log", frequency) {}

private:
  void periodic(bool force);
  void send(bool force);
  String cloudFunctionName() { return "sms"; }

  int sendSMS(const char* msg, const char* telNr);
  int processMessage(String messageText, String phoneReturn);
  void smsRecvCheck();
  void deleteSMSOnStart();

  virtual int cloudCommand(String command);
};
