# Electron Sensor Board Rev 3
This sensor board is part of the [GridWatch](http://grid.watch) project.

The goal of this board is to test data collection in Accra. It is built on
top of the Particle [Electron](https://docs.particle.io/datasheets/electron-(cellular)/electron-datasheet/)
as a shield and contains an IMU with accel/mag, a microphone, a light sensor, a
GPS, an SD card, and a BLE radio.

The current version is built to be deployed inside. Eventually, this platform
will be both indoors and solar powered. Right now, the peripherals are not
selected for low power... Instead, they are selected because I've used them
before.

This design has been modified from the original revision 2 based on testing and
deployment experiences. Particularly fixed are the problems tracked in
[Rev A Issues](https://github.com/lab11/PlugWatch/issues/2).

