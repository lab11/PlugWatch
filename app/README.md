PlugWatch Application
=====================

This document describes the general behavoir of PlugWatch.

PlugWatch Operation
-------------------

PlugWatch runs the GridWatch service, which monitors the charging state of the phone and reports
a [GridWatch event](#gridwatch-event) whenever the charging state changes (note: unlike GridWatch,
PlugWatch reports every charge state event change, even if it was determined that this outage was
not due to a power outage).

PlugWatch also runs the WitCollect (**?? Name**) service, which connects to a nearby
[WiTenergy](http://www.wittech.net/WiTenergy.html) power meter to collect additional data. PlugWatch
samples Wit data every second (** DOUBLE CHECK **) and generates a [WitData](#witdata) entry for every sample (**and transmits every sample to the server in real time?? **).

**XXX is this right?**
GridWatch and Wit data are stored in a local database. PlugWatch creates a new database every 24
hours. Every 24 hours, PlugWatch performs a [BatchUpload](#batchupload) of yesterday's database.

In addition to its primary faculites, includes two diagnostics mechanism. First, every 24h the app
generates a [Heartbeat Ping](#heartbeat-ping) that simply sends a message to the server indicating
this phone is alive. Additionally, PlugWatch tracks [Internal Analytics](#internal-analytics),
which are used to diagnose the app and the deployment. These analytics leverage the Firebase tool
to automatically synchronize data **XXX often via an alaram / via FB on phone service?**


### GridWatch event

TODO: 


### WitData

TODO


### BatchUpload

TODO


### Heartbeat Ping

TODO


### Internal Analytics

These metrics are collected to diagnose the health of the PlugWatch app and deployment, and to help
diagnose issues that may arise.

Internal analytics are reported to Firebase via **XXX**, they are sent periodically via **XXX** or
on the following set of events **XXX (app restart, others?)**. **TODO:** Analytics reporting is throttled
to at most 1 report per hour.

**TODO: Update with actual names used in Firebase / data format**

_Reported as of 2017-02-14:_

  - All commands recevied
    - via SMS
    - via Firebase
  - Number of GridWatch events
  - App Version
  - IMEI
  - Phone Location
  - Phone Experimental ID
  - Phone Experimental Group ID
  - Number of databases
  - All crashes

_To add:_

  - Analytics ID counter
  - Packet counts
    - Bluetooth received
    - Cellular sent/received
  - Wakeup counts
    - Counter for each wakeup path in the app (each callback essentially)
  - Disk usage
  - App memory usage
  - Phone battery level
  - Current charging current
  - Estiamted app battery usage / imapact (w/e the OS gives)
  - Time phone last rebooted
  - Time app last rebooted
  - Time last analytics report sent (before this one)

Wishlist
--------

  - Nearby WiFi SSIDs
  - Compression for database sends. 
  - Periodic "Wit still not reachable" message transmissions - don't rely (solely) on watchdogs killing-and-restarting app to cause these to be generated only on re-start. As reliability increases may wish to have less aggressive watchdog that lets app running longer to increase chances of capturing exactly when power returns.
  - Option for "near" real-time bulk upload of Wit readings - e.g. N minutes worth at a time. Maybe only do bulk/trigger bulk send if voltage within a "normal" range.
  
  
