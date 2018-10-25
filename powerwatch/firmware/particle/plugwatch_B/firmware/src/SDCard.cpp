#include <SdFat.h>

#include "Cloud.h"
#include "SDCard.h"

void SDCard::setup() {
  super::setup();

  pinMode(SD_ENABLE_PIN, OUTPUT);
  digitalWrite(SD_ENABLE_PIN, LOW);
}

void SDCard::loop() {
  super::loop();

  if (power_cycle_flag) {
    power_cycle_flag = false;
    PowerCycle();

    // Power cycling is slow, so don't do anything else this loop, let others go
    return;
  }

  if (read_filename != "") {
    log.append("Read " + read_filename);

    String sd_res = Read(read_filename);
    read_filename = "";

    Cloud::Publish(SD_READ_EVENT,sd_res);
  }
}

int SDCard::cloudCommand(String command) {
  if ((command == "cycle") || (command == "reboot")) {
    power_cycle_flag = true;
    return 0;
  }
  if (command.startsWith("read ")) {
    read_filename = command.substring(5);
    return 0;
  }

  return -1;
}

void SDCard::PowerCycle() {
	digitalWrite(SD_ENABLE_PIN, HIGH);
	delay(1000);
	digitalWrite(SD_ENABLE_PIN, LOW);
	delay(1000);
}

void SDCard::Write(String filename, String to_write) {
  log.debug("write begin: " + filename);
	if (!sd.begin(SD_CHIP_SELECT, SPI_HALF_SPEED)) {
		log.debug("CAN'T OPEN SD");
		Cloud::Publish(SD_ERROR_EVENT, "init");
    return;
	}
	File file_to_write;
	String time_str = String(Time.format(Time.now(), TIME_FORMAT_ISO8601_FULL));
	String final_to_write = time_str + String("|") + to_write;
	if (!file_to_write.open(filename, O_RDWR | O_CREAT | O_APPEND)) {
		//sd.errorHalt("opening for write failed");
		Serial.println(String("opening ") + String(filename) + String(" for write failed"));
		Cloud::Publish(SD_ERROR_EVENT, String(filename) + String(" write"));
		return;
	}
	file_to_write.println(final_to_write);
	file_to_write.close();
	log.debug(String("wrote : ") + String(filename) + String(":") + to_write);
}

String SDCard::Read(String filename) {
  log.debug("read begin: " + filename);
	File myFile;
	if (!myFile.open(filename, O_READ)) {
		//sd.errorHalt(String("opening ") + String(filename) + String(" for read failed"));
		Serial.println(String("opening ") + String(filename) + String(" for read failed"));
		Cloud::Publish(SD_ERROR_EVENT, String(filename) + String(" read"));
		return "string err";
	}
	Serial.println(String(filename) + String(" content:"));
	String res = "";
	res += String(myFile.fileSize());
	res += "\n";
	while (myFile.available()) {
		char cur = myFile.read();
		res += String(cur);
		Serial.write(cur);
	}
	/*
	   int data;
	   while ((data = myFile.read()) >= 0) {
	   Serial.write(data);
	   res += String(data);
	   }
	   Serial.println(res);
	 */
	myFile.close();
	return res;
}


/*
void volDmp() {
  cout << F("\nVolume is FAT") << int(sd.vol()->fatType()) << endl;
  cout << F("blocksPerCluster: ") << int(sd.vol()->blocksPerCluster()) << endl;
  cout << F("clusterCount: ") << sd.vol()->clusterCount() << endl;
  cout << F("freeClusters: ");
  uint32_t volFree = sd.vol()->freeClusterCount();
  cout <<  volFree << endl;
  float fs = 0.000512*volFree*sd.vol()->blocksPerCluster();
  cout << F("freeSpace: ") << fs << F(" MB (MB = 1,000,000 bytes)\n");
  cout << F("fatStartBlock: ") << sd.vol()->fatStartBlock() << endl;
  cout << F("fatCount: ") << int(sd.vol()->fatCount()) << endl;
  cout << F("blocksPerFat: ") << sd.vol()->blocksPerFat() << endl;
  cout << F("rootDirStart: ") << sd.vol()->rootDirStart() << endl;
  cout << F("dataStartBlock: ") << sd.vol()->dataStartBlock() << endl;
}
*/
