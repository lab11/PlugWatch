#include <SdFat.h>

#include "Cloud.h"
#include "SDCard.h"

void SDCard::setup() {
  super::setup();

  pinMode(SD_ENABLE_PIN, OUTPUT);
  pinMode(SD_INT_PIN, INPUT);

  PowerOn();
}

LoopStatus SDCard::loop() {
  super::loop();

  return FinishedSuccess;
}

String SDCard::getResult() {
  return result;
}

void SDCard::PowerOn() {
	digitalWrite(SD_ENABLE_PIN, HIGH);
	delay(1000);
}

void SDCard::PowerOff() {
  SPI.end();
	digitalWrite(SCK, LOW);
	digitalWrite(MISO, LOW);
	digitalWrite(MOSI, LOW);
	digitalWrite(SS, LOW);
	digitalWrite(SD_ENABLE_PIN, LOW);
	delay(1000);
}

bool SDCard::Write(String filename, String to_write) {
  /*
  log.debug("write begin: " + filename);
	if (!sd.begin(SD_CHIP_SELECT, SPI_HALF_SPEED)) {
		log.debug("CAN'T OPEN SD");
		//Cloud::Publish(SD_ERROR_EVENT, "init");
    return;
	}
	File file_to_write;
	String time_str = String(Time.format(Time.now(), TIME_FORMAT_ISO8601_FULL));
	String final_to_write = time_str + String("|") + to_write;
	if (!file_to_write.open(filename, O_WRITE | O_CREAT | O_APPEND)) {
		//sd.errorHalt("opening for write failed");
		Serial.println(String("opening ") + String(filename) + String(" for write failed"));
		Cloud::Publish(SD_ERROR_EVENT, String(filename) + String(" write"));
		return;
	}
	file_to_write.println(final_to_write);
	file_to_write.close();
	log.debug(String("wrote : ") + String(filename) + String(":") + to_write);
  */

  // Let's just make sure the SD card is plugged in
  if(digitalRead(SD_INT_PIN)) {
    // This means the card is not present
		Serial.println("SD Card Not Present");
    result = "0";
    return 1;
  } else {
    result = "1";
  }

  if (!sd.begin(SD_CHIP_SELECT, SPI_HALF_SPEED)) {
		Serial.println("CAN'T OPEN SD");
    return 1;
	}
	File file_to_write;
	String time_str = String(Time.format(Time.now(), TIME_FORMAT_ISO8601_FULL));
	String final_to_write = time_str + String("|") + String(to_write);
	if (!file_to_write.open(filename, O_WRITE | O_CREAT | O_AT_END)) {
		Serial.println(String("opening ") + String(filename) + String(" for write failed"));
		return 1;
	}
	int r = file_to_write.write(final_to_write.c_str());
  if (r < 0) {
    Serial.println("Writing to file failed");
    file_to_write.flush();
	  file_to_write.close();
    return 1;
  } else {
    Serial.printlnf("Successfully wrote %d bytes to file", r);
  }

  delay(1000);
  file_to_write.flush();
  file_to_write.close();
  delay(1000);

	Serial.println(String("wrote : ") + String(filename) + String(":") + to_write);
  return 0;
}

int SDCard::getSize(String filename) {

  if(digitalRead(SD_INT_PIN)) {
    // This means the card is not present
		Serial.println("SD Card Not Present");
    return -1;
  }

  if (!sd.begin(SD_CHIP_SELECT, SPI_HALF_SPEED)) {
		Serial.println("CAN'T OPEN SD");
    return -1;
	}
	File f;
	if (!f.open(filename, O_RDONLY)) {
		Serial.println(String("opening ") + String(filename) + String(" to read filesize failed"));
		return -1;
	}
  int size = f.fileSize();
	f.close();

  return size;
}

String SDCard::Read(String filename) {
  //log.debug("read begin: " + filename);
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
