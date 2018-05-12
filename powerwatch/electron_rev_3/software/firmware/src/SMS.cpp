#include <Particle.h>

#include "Cloud.h"
#include "SMS.h"
#include "FileLog.h"

#include "uCommand.h"

#define RADIO Cellular

void SMS::setup() {
  super::setup();

  int atResult;
  log.debug("SMS Entering sms setup");
  uCmd.setDebug(false);

  atResult = uCmd.setSMSMode(1);
  if(atResult == RESP_OK) {
      log.debug("SMS Text mode setup okay");
  } else {
      log.debug("SMS Did not set up SMS mode");
  }
  deleteSMSOnStart();
}

LoopStatus SMS::loop() {
  if (smsAvailableFlag == 1) {
    smsRecvCheck();
    smsAvailableFlag = 0;
  }
  return FinishedSuccess;
}

int SMS::sendSMS(const char* msg, const char* telNr) {
    if (smsSent <= smsLimit) {
        smsLimit = smsLimit + 1;
        int  ret;

        if (!RADIO.ready())
            {
            RADIO.on();
            RADIO.connect();
            if (!waitFor(RADIO.ready, 5000))  //The imapct of the 'wait' to other running code or new SMS's coming in has not been tested, you may want to see what happens with this...
                {
                  return -1;
                }
            }

            //This code instructs the Cellular module on the Particle Electron to send a SMS to 'telNr' with the message 'msg'
            //telNr and msg variabes come from the function input.
            ret = Cellular.command("AT+CMGF=1\r\n");
            ret = Cellular.command("AT+CMGS=\"%s%s\",145\r\n", (telNr[0] != '+') ? "+" : "", telNr);
            ret = Cellular.command("%s\x1a", msg);

            switch (ret)
            {
                case WAIT:
                  log.debug("SMS Waiting");
                case RESP_OK:
                  break;
                default:
                  log.debug("SMS Message not sent");
                  break;
            }

              return ret;
    }
    return 0;
}


//------This function lets you process the phone number & message of the person who sent a SMS to the particle device-------
//------This function is called in the 'for' loop that is in the smsRecvCheck function
//------The smsRecvCheck function is triggered due to the text "STARTUP(cellular_sms_received_handler_set(smsRecvCheck, NULL, NULL));" which is written at the very top of this page.
int SMS::processMessage(String messageText, String phoneReturn)
{
    //First check the message length, we are throwing out messages that are too long.
		//Why? Didn't have time to test the impact of multiple SMS's coming in if a SMS limit is 160characters. You may wish to try this and see what happens if commands are spread across two messages - I expect messy...
		//Avoid using special characters in your SMS's eg. { } as they can consume more than one 'character' - at least according to a Telstra blog. Stick to a-z 0-9 !@#$%^&*(),./
    if (messageText.length() > 158) //could set it to 160, but adding two buffer characters just to be over cautious.
    {
        sendSMS("Error: Message length is too long", phoneReturn);  //This will send a message back to the sender
        return 0;
    }

    //2nd step - Separate out the commands
	    //Commands get sent to the device in the format of: "1234,command,option"
		//The next set of code will separate our message string into 3 Strings

	//Variables to store the locations of each comma
    int loc1 = 0;
    int loc2 = 0;
    int loc3 = 0;

	//Variables to store the text between each comma (note we don't include the comma in the output strings - they are dropped)
    String smsProvided_userCode = "loading";
    String smsProvided_Command = "loading";
    String smsProvided_Command_Option = "loading";

    //Find the first comma & save the first command string to the 1st variable
        loc1 = messageText.indexOf(",");
        smsProvided_userCode = messageText.substring(0,loc1);
        Serial.println(smsProvided_userCode);
    //Find the second comma & save the second command string to the 1st variable
        loc2 = messageText.indexOf(",",loc1+1);
        smsProvided_Command = messageText.substring(loc1+1,loc2);
        Serial.println(smsProvided_Command);
    //Find the third comma & save the third command string to the 1st variable
        loc3 = messageText.indexOf(",",loc2+1);
        smsProvided_Command_Option = messageText.substring(loc2+1,loc3);
        Serial.println(smsProvided_Command_Option);

		//Check to see if the usercode matches what we have stored in memory
    if (smsProvided_userCode != userCode)
    {
        return 0; //Exiting as code does not match
        //We do not SMS back saying wrong code as this will just give the sender a chance to guess our pass-code
				//Note you can increase the code length, it is a string - so you can go a bit larger than 4 digits.
				//Keep in mind these codes are not encrypted in memory & could be brute forced with a lot of SMS's so don't use anything important to access other accounts
    }

    if (smsProvided_userCode == userCode)
    {
        //Continue if code was correct

		//Turn off the D7 LED if the SMS command is 'off'
        if (smsProvided_Command == "on")
            {
                digitalWrite(D7, HIGH);
                sendSMS("Turned On!", phoneReturn);
                return 1;
            }
        //Turn off the D7 LED if the SMS command is 'off'
        if (smsProvided_Command == "off")
            {
                digitalWrite(D7, LOW);
                sendSMS("Turned Off!", phoneReturn);
                return 1;
            }

		//Change the SMS User Code
        if (smsProvided_Command == "newcode")
            {
                if (smsProvided_Command_Option.length() != 4)
                {
                    sendSMS("New code must be 4 numerical digits", phoneReturn); //you can change this to any length - but don't exceed the length of a 'string' data type or a SMS 160char limit.
                    return 0;
                }
                else
                {
                    userCode = smsProvided_Command_Option;
                    //SAVE TO MEMORY CODE HERE - if you are saving the user code to stored memory (non-volatile memory) do it here
                    sendSMS("New code is: " + smsProvided_Command_Option, phoneReturn);
                    sendSMS("Device: " + moduleID + " code changed to: "+ smsProvided_Command_Option, alertNumber); //We send a msg to the 'alertNumber' so they know what is going on. This isn't a necessary step and could be removed.
                    return 1;
                }
            return 0;
            }

        if (smsProvided_Command == "newnumber")
            {
                if (smsProvided_Command_Option.length() != 12)
                {
                    sendSMS("Error: Alert number must be 12 digits and in this format: +614xxxxxxxx", phoneReturn);
                    return 0;
                }

                if (smsProvided_Command_Option.startsWith("+61"))
                {
                    sendSMS("Mobile alert number changed to: " + smsProvided_Command_Option, alertNumber);
                    //SAVE TO MEMORY CODE HERE if you want
                    //SAVE TO CURRENT MEMORY VARIABLE HERE IF you want
                    sendSMS("Mobile number changed to: " + smsProvided_Command_Option, phoneReturn);
                    return 1;
                }
                else
                {
                    sendSMS("Error: Mobile number must start with  +614 --only Australian numbers are supported. e.g. 0488.. becomes +61488..", phoneReturn);
                    return 0;
                }
            return 0;
            }

        //Command not recognised
        //This function catches any other text that doesn't match the if functions above.
            sendSMS("Correct pass code was used, but command is not recognised", phoneReturn);
            return 0;
    }
    //We don't recognise or message back on the wrong code, so just return 0 and end the function.
    //A particle publish message limit is (up to 255 bytes) - so let's limit by the same SMS character limit - minus 2 to be over safe again.
    if (messageText.length() > 158)
    {
        log.debug("Some muppet sent this to us: " + messageText);
    }
    //If the message text is too long, just return and do nothing
    return 0;
}


//This function is triggered by" STARTUP(cellular_sms_received_handler_set(smsRecvCheck, NULL, NULL));"
//It will ask the cellular module for all of the messages and then process each one
//Keep in mind here - I have found for every 1 SMS send, the for loop appears to run twice. So a '2nd' blank SMS seems to appear.
//You should check this with your own testing
//Given we do not act on a SMS that doesn't match the user code - this isn't much of an issue.
void SMS::smsRecvCheck()
{
        int i;
        // read next available messages
        if(uCmd.checkMessages(10000) == RESP_OK){
            uCmd.smsPtr = uCmd.smsResults;
            for(i=0;i<uCmd.numMessages;i++){
                Serial.printlnf(uCmd.smsPtr->sms);
                Serial.printlnf(uCmd.smsPtr->phone);
                messageText = String(uCmd.smsPtr->sms);
                messageText = messageText.toLowerCase();
                phoneReturn = String(uCmd.smsPtr->phone);
                phoneReturn = phoneReturn.trim();
                processMessage(messageText, phoneReturn);   ///---This is the line that will let us process the message. Note each message is processed 1 at a time. So if a message spans two SMS's it may not be picked up as a 'single' SMS. So keep the messages short and less than 106characters.
                uCmd.smsPtr++;
            }
        }
        //----Delete SMS's----
        //We don't want to keep processing the same messages again and again. So delete them from the buffer after we processed them.
        //The buffer will fill up eventually if we don't.. It is unknown what happens when the buffer fills up.


        uCmd.smsPtr = uCmd.smsResults;

        for(i=0;i<uCmd.numMessages;i++){
            if(uCmd.deleteMessage(uCmd.smsPtr->mess,10000) == RESP_OK)
            {
                Serial.println("message deleted successfully");
            }
            else
            {
                Serial.println("could not delete message");  //You will see this message occur for the 'last' empty message every time.
            }
            uCmd.smsPtr++;
        }

}

void SMS::deleteSMSOnStart()
{
    int i;
    for(i=0;i<uCmd.numMessages;i++){
            if(uCmd.deleteMessage(uCmd.smsPtr->mess,10000) == RESP_OK)
            {
                Serial.println("message deleted successfully");
            }
            else
            {
                Serial.println("could not delete message"); //You will see this message occur for the 'last' empty message every time.
            }
            uCmd.smsPtr++;
        }
}

void SMS::smsRecvFlag(void* data, int index)
{
    smsAvailableFlag = 1;
}
