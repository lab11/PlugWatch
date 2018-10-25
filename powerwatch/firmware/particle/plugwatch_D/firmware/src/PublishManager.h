#pragma once

/* PublishManager library by Ben Veenema, Noah Klugman
 */

#include "Particle.h"
#include <deque>

class PublishManager
{
public:
  /**
   * Constructor - Creates 1 second Software Timer to automagically publish
   *               without calling a "process" method
   */
  PublishManager() : publishTimer(1000, &PublishManager::publishTimerCallback, *this, false) {
      publishTimer.start();
  };

  const uint8_t HIGH = 1;
  const uint8_t LOW = 0;

  /**
   * publish -  Publishes event immediately if timer has elapsed or adds a
   *            publish event (pubEvent) to the queue
   *            Returns true if event is published or added to queue. Returns
   *            false is the queue is full and event is discarded
   */
   bool publish(String eventName, String data, int priority) {

     if (priority == HIGH) { //Added if high priority force add to the queue
       //cout << "\tHIGH ADDED" << "\n";
       if (pubQueue.size() >= _maxCacheSize) {
         //cout << "\tHIGH WILL EVICT" << "\n";
         bool found_low = false;
         //put high priority event in the oldest low priority spot
         for (std::deque<pubEvent>::iterator it = pubQueue.begin(); it!=pubQueue.end(); ++it) {
            if ((*it).priority == LOW) {
               pubQueue.erase(it);
               found_low = true;
               //cout << "\tFOUND LOW TO EVICT \n";
               break;
            }
         }
         if (!found_low) { //if no place pop the  (oldest)
           pubQueue.pop_front();
           //cout << "\tNO LOW FOUND... EVICTING FRONT \n";
         }
       }
     }

     if(pubQueue.size() >= _maxCacheSize) {
         //cout << "\tAT MAX" << "\n";
         return false;
     }

     if(FLAG_canPublish && pubQueue.empty() && !first) {
       if (first) {
           first = false;
       }
       FLAG_canPublish = false;
     } else {
       pubEvent newEvent = {.eventName=eventName, .data=data, .priority=priority};
       pubQueue.push_back(newEvent);
     }
     return true;
   };

  /**
   * maxCacheSize - Sets the max cache size for pubQueue. Default 10
   */
  void maxCacheSize(uint8_t newMax) {
    _maxCacheSize = newMax;
  }

  /**
   * process -  RESERVED - may be used in future if library is to be used
   *            without software timer
   */
  void process();

private:
  struct pubEvent {
      String eventName;
      String data;
      int priority;
  };
  bool first = true;
  std::deque<pubEvent> pubQueue;
  Timer publishTimer;
  bool FLAG_canPublish = true;

  uint8_t _maxCacheSize = 10;

  /**
   * publishTimerCallback - Removes the front element from the queue and publishes
   *                        If there is no element in the queue, sets FLAG_canPublish
   */
   void publishTimerCallback() {
       if (!pubQueue.empty() && Particle.connected()) {
         pubEvent frontEvent = pubQueue.front();
         pubQueue.pop_front();
         Particle.publish(frontEvent.eventName, frontEvent.data, 60, PRIVATE);
         FLAG_canPublish = false;
       }else{
         FLAG_canPublish = true;
       }
   };
};
