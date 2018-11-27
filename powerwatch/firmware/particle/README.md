Plugwatch sensor firmware
=========================

This folder holds all firmware running on plugwatch sensors. Currently
the two active sub-folders are plugwatch_C and plugwatch_F. 
Other folders hold testing revisions.

Firmware versioning is a 6 digit decimal number. 
Plugwatch C firmware starts at 000000 and plugwatch F firmware starts at 000100.

##Revision list
 - 000026 - Currently deployed on Ghana and Venezuela
 - 0000100 - The most recent version of the new firmware. Still in development, not deployed.

##To deploy firmware

 1) Commit your changes with the new version number.
 2) Build the versions for all of the product IDs and APNs - a script to do this will be coming soon.
 3) Push your builds to the particle cloud using the firmware deployment script - soon to be modified to take a folder of builds
 4) Commit the most recent firmware build folder


Getting Started with Firmware Development
-----------------------------------------

Install the `particle-cli`, ideally just:

    npm install -g particle-cli

But I had to then also:

    npm i -g node-gyp
    ls ~/.particle     # note node version, mine was v5.4.1
    nvm use 5.4.1
    cd ~/.particle/node_modules/serialport
    node-gyp rebuild

Then blow out that terminal becase you don't really want that `nvm use` to persist.
If everything's hunky dory, you can now run `particle --version` or some such.


### Compiling

    particle compile electron
    
#### Compiling Locally

The paricle CLI will use the cloud still, which is annoyingly slow IMHO.
While there's theoretically a path to _actual_ local compilation, down that road
lies madness. Your best bet is their [Atom plugin](https://atom.io/packages/particle-dev-local-compiler).
You'll need to install Docker first. Compiles are still slower than I'd like,
but only a few seconds at least.

#### Cryptic error / error you didn't have before?

For example `error: 'LOW_BAT_UC' was not declared in this scope`

Make sure that you're building for the right device (an `electron`).
The IDE in particular tends to like to forget what you're building for,
especially across open/closing.

### Claim the particle (must do once)

  0. Plug in particle via USB
  1. Enter 'listening mode': Hold `MODE` button for three seconds, until the
     LED starts blinking blue.
  2. `particle identify`
  3. `particle device add <device id from last step>`

### Flashing

  1. Enter 'dfu mode': Reset and then hold `MODE` button for three seconds,
     until the LED starts blinking yellow.
  2. `particle flash --usb <filename>.bin`
  
#### Flashing made easier

`stty -f /dev/tty.usbmodem14321 14400 && particle flash --usb $(ls -t | grep firmware | head -1)`

The first part puts the particle into DFU mode. The last part chooses the most
recent file based on filesystem timestamp.
  
### Listening to Serial

I recommend `particle serial monitor --follow`

Annoyingly, every time the particle resets, the modem disappears and reappears.
The `--follow` flag will automatically reconnect, but there's a bit of a delay,
so you're gonna lose the first few messages. I've never solved this puzzle.
