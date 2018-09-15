//***********************************
//* SD LOG HELPERS
//***********************************

#include <Particle.h>

#include "Cloud.h"
#include "FileLog.h"
#include "SDCard.h"

void FileLog::processIsrQueue() {
  String to_write = "";
  while (!isr_queue.empty()) {
    to_write += isr_queue.front() + "\n";
    isr_queue.pop();
  }
  if (to_write != "") {
    Serial.println("Writing enqueued log messages to SD");
    sd.Write(filename, to_write);
  }
}

void FileLog::appendFromISR(String str) {
  Serial.println(filename + ": " + str);
  isr_queue.push(str);
}

bool FileLog::append(String str) {
  processIsrQueue();

  String fname = String(current_name) + String('_') + filename;
  int size = sd.getSize(fname);
  if(current_name[0] == 0 || size == -1 || size > 65000) {
    //generate a new filename
    randomSeed(millis());
    int r = random(100000000);
    snprintf(current_name,50,"%d",r);
  }

  fname = String(current_name) + String('_') + filename;
  Serial.println(fname + ": " + str);
  return sd.Write(fname, str + "\n");
}

void FileLog::errorFromISR(String str) {
  Serial.println("ERROR|" + filename + ": " + str);
  isr_queue.push(str);
}

bool FileLog::error(String str) {
  processIsrQueue();

  Serial.println("ERROR|" + filename + ": " + str);
  Cloud::Publish(ERROR_EVENT, str);
  return sd.Write(filename, "ERROR|" + str + "\n");
}

void FileLog::debugFromISR(String str) {
  Serial.println("DEBUG|" + filename + ": " + str);
  isr_queue.push(str);
}

void FileLog::debug(String str) {
  processIsrQueue();

  Serial.println("DEBUG|" + filename + ": " + str);
}

int FileLog::getFileSize() {
  String fname = String(current_name) + String('_') + filename;
  return sd.getSize(fname);
}

String FileLog::getCurrentName() {
  return String(current_name);
}
