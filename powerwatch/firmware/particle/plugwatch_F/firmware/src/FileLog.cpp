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

  String fname = filename;

  Serial.println(fname + ": " + str);
  return sd.Write(fname, str + "\n");
}

bool FileLog::appendAndRotate(String str, uint32_t unixTime) {
  processIsrQueue();

  //rotate logs every day
  //get the day month year
  struct tm  * time;
  time = gmtime((time_t*)&unixTime);

  String fname = String(1900 + time->tm_year) + String('-') + 
                 String(time->tm_mon + 1) + String('-') + 
                 String(time->tm_mday) + String('_') + filename;

  Serial.println(fname + ": " + str);
  return sd.Write(fname, str + "\n");
}

String FileLog::getLastLine() {
  processIsrQueue();
  return sd.getLastLine(filename);
}

bool FileLog::removeLastLine() {
  processIsrQueue();
  return sd.removeLastLine(filename);
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
  return sd.getSize(filename);
}

int FileLog::getRotatedFileSize(uint32_t unixTime) {
  struct tm  * time;
  time = gmtime((time_t*)&unixTime);

  String fname = String(1900 + time->tm_year) + String('-') + 
                 String(time->tm_mon + 1) + String('-') + 
                 String(time->tm_mday) + String('_') + filename;


  return sd.getSize(fname);
}
