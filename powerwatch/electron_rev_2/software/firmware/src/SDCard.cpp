#include <SdFat.h>

#include "Cloud.h"
#include "SDCard.h"

const uint8_t SD_INT_PIN = D6;
const uint8_t SD_ENABLE_PIN = A6;
const uint8_t SD_CHIP_SELECT = A2;

//SdFatSoftSpi<A5, A4, A3> sd; // rev2 HW
SdFatSoftSpi<A4, A5, A3> sd; // rev1 HW

void SDCard::setup() {
   pinMode(SD_ENABLE_PIN, OUTPUT);
}

void SDCard::PowerCycle() {
	digitalWrite(SD_ENABLE_PIN, HIGH);
	delay(1000);
	digitalWrite(SD_ENABLE_PIN, LOW);
	delay(1000);
}

void SDCard::Write(String filename, String to_write) {
	if (!sd.begin(SD_CHIP_SELECT, SPI_HALF_SPEED)) {
		Serial.println("CAN'T OPEN SD");
		Cloud::Publish(SD_ERROR_EVENT, "init");
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
	Serial.println(String("wrote : ") + String(filename) + String(":") + to_write);
}

String SDCard::Read(String filename) {
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
