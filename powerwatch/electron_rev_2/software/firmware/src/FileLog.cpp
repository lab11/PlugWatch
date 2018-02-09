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
    //sd.Write(filename, to_write);
  }
}

void FileLog::appendFromISR(String str) {
  Serial.println(filename + ": " + str);
  isr_queue.push(str);
}

void FileLog::append(String str) {
  processIsrQueue();

  Serial.println(filename + ": " + str);
  //sd.Write(filename, str + "\n");
}

void FileLog::errorFromISR(String str) {
  Serial.println("ERROR|" + filename + ": " + str);
  isr_queue.push(str);
}

void FileLog::error(String str) {
  processIsrQueue();

  Serial.println("ERROR|" + filename + ": " + str);
  Cloud::Publish(ERROR_EVENT, str);
  //sd.Write(filename, "ERROR|" + str + "\n");
}

void FileLog::debugFromISR(String str) {
  Serial.println("DEBUG|" + filename + ": " + str);
  isr_queue.push(str);
}

void FileLog::debug(String str) {
  processIsrQueue();

  Serial.println("DEBUG|" + filename + ": " + str);
}
