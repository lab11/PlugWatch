# Electron Sensor Board Rev 2
This sensor board is part of the [GridWatch](http://grid.watch) project.

The goal of this board is to test data collection in Accra. It contains 
an IMU with accel/mag and temp, two different microphones (one analog and
one digial), some memory, and a ultra-low power timer for future low-power 
optimizations. 

This directory will host development for all electron software and firmware.


Getting Started
---------------

Install the `particle-cli`, ideally just:

    `npm install -g particle-cli`

But I had to then also:

    `npm i -g node-gyp`
    `ls ~/.particle` # note node version, mine was v5.4.1
    `nvm use 5.4.1`
    `cd ~/.particle/node_modules/serialport`
    `node-gyp rebuild`

Then blow out that terminal becase you don't really want that `nvm use` to persist.
If everything's hunky dory, you can now run `particle --version` or some such.


### Compiling

    `particle compile electron`

### Claim the particle (must do once)

    0. Plug in particle via USB
    1. Enter 'listening mode': Hold `MODE` button for three seconds, until the
       LED starts blinking blue.
    2. `particle identify`
    3. `particle device add <device id from last step>`

### Flashing

    1. Enter 'dfu mode': Reset and then hold `MODE` button for three seconds,
       until the LED starts blinking yellow.
    2. `particle flash --usb <filename>.bin
