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
  if (!file_to_write.open(filename, O_WRITE | O_CREAT | O_AT_END)) {
    Serial.println(String("opening ") + String(filename) + String(" for write failed"));
    return 1;
  }

  int r = file_to_write.write(to_write.c_str());
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

//reads a file starting at position until a newline
String SDCard::ReadLine(String filename, uint32_t position) {
	File myFile;
	if (!myFile.open(filename, O_READ)) {
		Serial.println(String("opening ") + String(filename) + String(" for read failed"));
		Cloud::Publish(SD_ERROR_EVENT, String(filename) + String(" read"));
		return "string err";
	}
	Serial.println(String(filename) + String(" content:"));

	//seek to position
	myFile.seek(position);

	String res = "";
	while (myFile.available()) {
		char cur = myFile.read();
		if(cur == '\n') {
		  break;
		} else {
		  res += String(cur);
		}
	}
	myFile.close();
	return res;
}

String SDCard::getLastLine(String filename) {
  // Let's just make sure the SD card is plugged in
  if(digitalRead(SD_INT_PIN)) {
    // This means the card is not present
    Serial.println("SD Card Not Present");
    result = "0";
    return "";
  } else {
    result = "1";
  }

  if (!sd.begin(SD_CHIP_SELECT, SPI_HALF_SPEED)) {
    Serial.println("CAN'T OPEN SD");
    return "";
  }

  File file_to_write;
  if (!file_to_write.open(filename, O_READ)) {
    Serial.println(String("opening ") + String(filename) + String(" for write failed"));
    return "";
  }

  //get the length of the file
  int current_length = file_to_write.fileSize();

  //now seek from the end an appropriate amount
  //reading forward and recording the position of the last newline

  if(current_length > 0) {
    //seek backwards either 2KB or to the beginning of the file
    if(current_length > 2000) {
      if(!file_to_write.seekEnd(2000)) {
	Serial.println("Seek failed");
	file_to_write.close();
	return "";
      }
    } else {
      //seek to beginning
      if(!file_to_write.seekSet(0)) {
	Serial.println("Seek failed");
	file_to_write.close();
	return "";
      }
    }

    int newline_pos = 0;
    int last_newline_pos = 0;

    //okay now linearly scan recording the last newline before the end
    while (file_to_write.available()) {
      char cur = file_to_write.read();
      if(cur == '\n') {
	if(last_newline_pos == 0) {
	  last_newline_pos = file_to_write.position();
	} else {
	  newline_pos = last_newline_pos;
	  last_newline_pos = file_to_write.position();
	}
      }
    }

    //now return from newline_pos to EOF
    file_to_write.seekSet(newline_pos);
    String res = "";
    while (file_to_write.available()) {
      char cur = file_to_write.read();
      res += String(cur);
    }

    file_to_write.close();
    return res;

  } else {
    Serial.println("empty file - nothing to return");
    file_to_write.close();
    return "";
  }
}

bool SDCard::removeLastLine(String filename) {
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
  if (!file_to_write.open(filename, O_WRITE | O_READ)) {
    Serial.println(String("opening ") + String(filename) + String(" for write failed"));
    return 1;
  }

  //get the length of the file
  int current_length = file_to_write.fileSize();
  Serial.print("Current length: ");
  Serial.println(current_length);

  //now seek from the end an appropriate amount
  //reading forward and recording the position of the last newline

  if(current_length > 0) {
    //seek backwards either 2KB or to the beginning of the file
    if(current_length > 2000) {
      Serial.println("Setting seek to end minus 2000 chars");
      if(!file_to_write.seek(current_length - 2000)) {
	Serial.println("Seek failed");
	file_to_write.close();
	return 1;
      }
    } else {
      //seek to beginning
      Serial.println("Setting seek to beginning of file");
      if(!file_to_write.seek(0)) {
	Serial.println("Seek failed");
	file_to_write.close();
	return 1;
      }
    }

    int newline_pos = 0;
    int last_newline_pos = 0;

    //okay now linearly scan recording the last newline before the end
    Serial.println("Scanning file for newlines");
    while (file_to_write.available()) {
      Serial.println(file_to_write.available());
      char cur = file_to_write.read();
      if(cur == '\n') {
	int pos = file_to_write.position();
	Serial.print("Found newline at ");
        Serial.println(pos);
	if(last_newline_pos == 0) {
	  last_newline_pos = pos;
	} else {
	  newline_pos = last_newline_pos;
	  last_newline_pos = pos;
	}
      }
    }

    Serial.print("truncating file at ");
    Serial.println(newline_pos);

    //okay truncate the file to the newline pos
    if(!file_to_write.truncate(newline_pos)) {
      Serial.println("Error truncating");
      file_to_write.close();
      return 1;
    }

    delay(1000);
    file_to_write.flush();
    file_to_write.close();
    return 0;

  } else {
    Serial.println("empty file - nothing to remove");
    file_to_write.close();
    return 1;
  }
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

  myFile.close();
  return res;
}
