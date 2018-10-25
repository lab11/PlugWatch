# Electron Sensor Board Rev 1
This sensor board is part of the [GridWatch](http://grid.watch) project.

The goal of this board is to test data collection in Accra. It contains 
an IMU with accel/mag and temp, two different microphones (one analog and
one digial), some memory, and a ultra-low power timer for future low-power 
optimizations. 

The current version is built to be deployed inside. Eventually, this platform
will be both indoors and solar powered. Right now, the peripherals are not
selected for low power... Instead, they are selected because I've used them before. 

Eventually, there will only be a single type of microphone. I believe this will be
the digital mic, but I don't have the I2S driver written yet, so I'm including the 
analog mic as a backup. 

We will likely be building 10 of these and then redesigning.
